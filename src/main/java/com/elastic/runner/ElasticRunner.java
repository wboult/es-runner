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
        return start(ElasticRunnerConfig.defaults().toBuilder().distroZip(distroZip).build());
    }

    public static ElasticServer start(String version) {
        return start(ElasticRunnerConfig.defaults().toBuilder().version(version).build());
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
        Path zip = resolveDistroZipInternal(config);

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
        Path pidFile = versionDir.resolve("es.pid");
        Path stateFile = versionDir.resolve("state.json");
        Process process = startProcess(homeDir, configDir, config, pidFile);
        Thread logThread = new Thread(new StreamGobbler(process.getInputStream(), logFile, config.quiet()),
                "elastic-runner-log");
        logThread.setDaemon(true);
        logThread.start();

        try {
            int actualPort = httpPort == 0
                    ? waitForEphemeralPort(process, logFile, config.startupTimeout())
                    : httpPort;
            Instant startTime = Instant.now();
            URI baseUri = URI.create("http://localhost:" + actualPort + "/");
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();

            waitForReady(httpClient, baseUri, config.startupTimeout(), process, logFile);

            long serverPid = readServerPid(pidFile).orElse(process.pid());

            writeState(stateFile, new RunnerState(
                    serverPid,
                    actualPort,
                    config.clusterName(),
                    version,
                    startTime,
                    versionDir,
                    baseUri.toString()
            ));

            return new ElasticServer(
                    config,
                    process,
                    homeDir,
                    versionDir,
                    logFile,
                    pidFile,
                    stateFile,
                    serverPid,
                    actualPort,
                    httpClient,
                    startTime,
                    logThread
            );
        } catch (RuntimeException e) {
            cleanupFailedStart(process, pidFile, stateFile, logThread);
            throw e;
        }
    }

    public static Path resolveDistroZip(ElasticRunnerConfig config) {
        Objects.requireNonNull(config, "config");
        return resolveDistroZipInternal(config);
    }

    private static Path resolveDistroZipInternal(ElasticRunnerConfig config) {
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
        DistroDownloader.download(uri, target);
    }

    private static int resolvePort(ElasticRunnerConfig config) {
        return config.httpPort();
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
                                        ElasticRunnerConfig config,
                                        Path pidFile) {
        Path script = homeDir.resolve("bin").resolve(Os.executableName("elasticsearch"));
        List<String> command = Os.commandFor(script);
        command = new java.util.ArrayList<>(command);
        command.add("-p");
        command.add(pidFile.toString());
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

    private static void writeState(Path stateFile, RunnerState state) {
        try {
            state.write(stateFile);
        } catch (IOException e) {
            throw new ElasticRunnerException("Failed to write state file.", e);
        }
    }

    private static void cleanupFailedStart(Process process,
                                           Path pidFile,
                                           Path stateFile,
                                           Thread logThread) {
        try {
            long serverPid = readServerPid(pidFile).orElse(process.pid());
            ProcessTree.terminate(process, serverPid, Duration.ofSeconds(10));
            if (logThread.isAlive()) {
                logThread.join(TimeUnit.SECONDS.toMillis(2));
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            deleteIfExists(pidFile);
            deleteIfExists(stateFile);
        }
    }

    private static java.util.OptionalLong readServerPid(Path pidFile) {
        if (!Files.exists(pidFile)) {
            return java.util.OptionalLong.empty();
        }
        try {
            String value = Files.readString(pidFile, StandardCharsets.UTF_8).trim();
            if (value.isEmpty()) {
                return java.util.OptionalLong.empty();
            }
            return java.util.OptionalLong.of(Long.parseLong(value));
        } catch (IOException | NumberFormatException ignored) {
            return java.util.OptionalLong.empty();
        }
    }

    private static void createDirs(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ElasticRunnerException("Failed to create directory: " + dir, e);
        }
    }

    private static void deleteIfExists(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    private static int waitForEphemeralPort(Process process, Path logFile, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        java.util.regex.Pattern portPattern = java.util.regex.Pattern.compile("publish_address \\{[^:]+:(\\d+)\\}");
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                String logTail = LogTail.read(logFile, 20);
                throw new ElasticRunnerException("Elasticsearch exited unexpectedly before binding port: " + logTail);
            }
            try {
                if (Files.exists(logFile)) {
                    List<String> lines = Files.readAllLines(logFile);
                    for (String line : lines) {
                        java.util.regex.Matcher matcher = portPattern.matcher(line);
                        if (matcher.find()) {
                            return Integer.parseInt(matcher.group(1));
                        }
                    }
                }
            } catch (IOException ignored) {
            }
            sleep(500);
        }
        String logTail = LogTail.read(logFile, 20);
        throw new ElasticRunnerException("Timed out waiting for ephemeral port bind: " + logTail);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
