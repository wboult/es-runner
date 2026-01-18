package com.elastic.runner;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public final class ElasticRunner {
    private ElasticRunner() {
    }

    public static ElasticServer start(Path distroZip) {
        return start(ElasticRunnerConfig.defaults().withDistroZip(distroZip));
    }

    public static ElasticServer start(String version) {
        return start(ElasticRunnerConfig.defaults().withVersion(version));
    }

    public static ElasticServer start(UnaryOperator<ElasticRunnerConfig> configurer) {
        return start(configurer.apply(ElasticRunnerConfig.defaults()));
    }

    public static ElasticServer start(Consumer<ElasticRunnerConfig.Builder> consumer) {
        return start(ElasticRunnerConfig.from(consumer));
    }

    public static void withServer(ElasticRunnerConfig config, Consumer<ElasticServer> action) {
        try (ElasticServer server = start(config)) {
            action.accept(server);
        }
    }

    public static <T> T withServer(ElasticRunnerConfig config, Function<ElasticServer, T> action) {
        try (ElasticServer server = start(config)) {
            return action.apply(server);
        }
    }

    public static void withServer(Consumer<ElasticRunnerConfig.Builder> configurer,
                                  Consumer<ElasticServer> action) {
        withServer(ElasticRunnerConfig.from(configurer), action);
    }

    public static <T> T withServer(Consumer<ElasticRunnerConfig.Builder> configurer,
                                   Function<ElasticServer, T> action) {
        return withServer(ElasticRunnerConfig.from(configurer), action);
    }

    public static ElasticServer start(ElasticRunnerConfig config) {
        Objects.requireNonNull(config, "config");
        Path zip = resolveDistroZip(config);

        String version = config.version() != null ? config.version() : DistroVersion.fromZip(zip);
        Path workDir = config.workDir().toAbsolutePath();
        Path versionDir = workDir.resolve(version);
        createDirs(versionDir);

        Path homeDir;
        try {
            homeDir = DistroLayout.prepare(zip, versionDir);
        } catch (IOException e) {
            throw new ElasticRunnerException("Failed to prepare Elasticsearch distribution.", e);
        }

        Path dataDir = versionDir.resolve("data");
        Path logsDir = versionDir.resolve("logs");
        createDirs(dataDir);
        createDirs(logsDir);

        int httpPort = resolvePort(config);

        Path configDir = homeDir.resolve("config");
        Path configFile = configDir.resolve("elasticsearch.yml");
        writeConfig(config, httpPort, dataDir, logsDir, configFile);

        if (!config.plugins().isEmpty()) {
            installPlugins(config.plugins(), homeDir, logsDir, config.quiet());
        }

        Path logFile = logsDir.resolve("runner.log");
        Process process = startProcess(homeDir, configDir, config);
        Thread logThread = new Thread(new StreamGobbler(process.getInputStream(), logFile, config.quiet()),
                "elastic-runner-log");
        logThread.setDaemon(true);
        logThread.start();

        Path pidFile = versionDir.resolve("es.pid");
        Path stateFile = versionDir.resolve("state.json");
        writePid(pidFile, process.pid());

        Instant startTime = Instant.now();
        URI baseUri = URI.create("http://localhost:" + httpPort + "/");
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        writeState(stateFile, new RunnerState(
                process.pid(),
                httpPort,
                config.clusterName(),
                version,
                startTime,
                versionDir,
                baseUri.toString()
        ));

        waitForReady(httpClient, baseUri, config.startupTimeout(), process, logFile);

        return new ElasticServer(
                config,
                process,
                homeDir,
                versionDir,
                logFile,
                pidFile,
                stateFile,
                httpPort,
                httpClient,
                startTime,
                logThread
        );
    }

    private static Path resolveDistroZip(ElasticRunnerConfig config) {
        if (config.distroZip() != null) {
            return requireZip(config.distroZip());
        }
        if (config.version() == null || config.version().isBlank()) {
            throw new ElasticRunnerException("version or distroZip is required.");
        }
        Path distrosDir = config.distrosDir().toAbsolutePath();
        createDirs(distrosDir);
        DistroDescriptor descriptor = DistroDescriptor.forVersion(config.version());
        Path archive = distrosDir.resolve(descriptor.fileName());
        if (config.download() || !Files.exists(archive)) {
            download(descriptor.downloadUri(config.downloadBaseUrl()), archive);
        }
        return requireZip(archive);
    }

    private static Path requireZip(Path zip) {
        if (!Files.exists(zip)) {
            throw new ElasticRunnerException("Distro archive does not exist: " + zip);
        }
        if (!Files.isRegularFile(zip)) {
            throw new ElasticRunnerException("Distro archive is not a file: " + zip);
        }
        return zip;
    }

    private static void download(URI uri, Path target) {
        Path temp = target.resolveSibling(target.getFileName() + ".partial");
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();
            createDirs(target.getParent());
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(temp));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ElasticRunnerException("Failed to download distro: " + uri + " (HTTP " + response.statusCode() + ")");
            }
            Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ElasticRunnerException("Failed to download distro from " + uri, e);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
        }
    }

    private static int resolvePort(ElasticRunnerConfig config) {
        if (config.httpPort() > 0) {
            return config.httpPort();
        }
        int port = PortPicker.pick(config.portRangeStart(), config.portRangeEnd());
        if (port <= 0) {
            throw new ElasticRunnerException("No free port found in range "
                    + config.portRangeStart() + "-" + config.portRangeEnd());
        }
        return port;
    }

    private static void writeConfig(ElasticRunnerConfig config,
                                    int httpPort,
                                    Path dataDir,
                                    Path logsDir,
                                    Path configFile) {
        Map<String, String> settings = new LinkedHashMap<>(config.settings());
        settings.putIfAbsent("cluster.name", config.clusterName());
        settings.put("path.data", dataDir.toString());
        settings.put("path.logs", logsDir.toString());
        settings.put("http.port", Integer.toString(httpPort));
        try {
            ConfigWriter.write(configFile, settings);
        } catch (IOException e) {
            throw new ElasticRunnerException("Failed to write elasticsearch.yml", e);
        }
    }

    private static void installPlugins(List<String> plugins,
                                       Path homeDir,
                                       Path logsDir,
                                       boolean quiet) {
        Path pluginScript = homeDir.resolve("bin").resolve(Os.executableName("elasticsearch-plugin"));
        for (String plugin : plugins) {
            List<String> command = Os.commandFor(pluginScript);
            List<String> commandWithArgs = new java.util.ArrayList<>(command);
            commandWithArgs.add("install");
            commandWithArgs.add("--batch");
            commandWithArgs.add(plugin);
            ProcessBuilder builder = new ProcessBuilder(commandWithArgs);
            builder.directory(homeDir.toFile());
            builder.redirectErrorStream(true);
            Path logFile = logsDir.resolve("plugin-" + plugin.replaceAll("[^a-zA-Z0-9._-]", "_") + ".log");
            try {
                Process process = builder.start();
                Thread logThread = new Thread(new StreamGobbler(process.getInputStream(), logFile, quiet),
                        "elastic-runner-plugin-" + plugin);
                logThread.setDaemon(true);
                logThread.start();
                if (!process.waitFor(5, TimeUnit.MINUTES) || process.exitValue() != 0) {
                    throw new ElasticRunnerException("Failed to install plugin: " + plugin);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ElasticRunnerException("Plugin install interrupted: " + plugin, e);
            } catch (IOException e) {
                throw new ElasticRunnerException("Plugin install failed: " + plugin, e);
            }
        }
    }

    private static Process startProcess(Path homeDir,
                                        Path configDir,
                                        ElasticRunnerConfig config) {
        Path script = homeDir.resolve("bin").resolve(Os.executableName("elasticsearch"));
        List<String> command = Os.commandFor(script);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(homeDir.toFile());
        builder.redirectErrorStream(true);
        Map<String, String> env = builder.environment();
        env.put("ES_PATH_CONF", configDir.toString());
        env.put("ES_JAVA_OPTS", "-Xms" + config.heap() + " -Xmx" + config.heap());
        try {
            return builder.start();
        } catch (IOException e) {
            throw new ElasticRunnerException("Failed to start Elasticsearch process.", e);
        }
    }

    private static void waitForReady(HttpClient httpClient,
                                     URI baseUri,
                                     Duration timeout,
                                     Process process,
                                     Path logFile) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                String logTail = LogTail.read(logFile, 20);
                throw new ElasticRunnerException("Elasticsearch process exited early. " + logTail);
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(baseUri)
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ElasticRunnerException("Interrupted while waiting for Elasticsearch.", e);
            } catch (IOException ignored) {
                // Retry until timeout.
            }
            sleep(500);
        }
        String logTail = LogTail.read(logFile, 20);
        throw new ElasticRunnerException("Timed out waiting for Elasticsearch. " + logTail);
    }

    private static void writePid(Path pidFile, long pid) {
        try {
            Files.createDirectories(pidFile.getParent());
            Files.writeString(pidFile, Long.toString(pid), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ElasticRunnerException("Failed to write PID file.", e);
        }
    }

    private static void writeState(Path stateFile, RunnerState state) {
        try {
            state.write(stateFile);
        } catch (IOException e) {
            throw new ElasticRunnerException("Failed to write state file.", e);
        }
    }

    private static void createDirs(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ElasticRunnerException("Failed to create directory: " + dir, e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
