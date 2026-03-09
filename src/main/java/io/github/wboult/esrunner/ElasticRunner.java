package io.github.wboult.esrunner;

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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Entry point for resolving Elasticsearch and OpenSearch distributions and
 * starting local process-based clusters.
 */
public final class ElasticRunner {
    private ElasticRunner() {
    }

    /**
     * Starts a distro from a local ZIP archive using default settings.
     *
     * @param distroZip distro ZIP archive
     * @return running server handle
     */
    public static ElasticServer start(Path distroZip) {
        return start(ElasticRunnerConfig.defaults().toBuilder().distroZip(distroZip).build());
    }

    /**
     * Starts Elasticsearch for the given version using default distro download
     * resolution rules.
     *
     * @param version Elasticsearch version
     * @return running server handle
     */
    public static ElasticServer start(String version) {
        return start(ElasticRunnerConfig.defaults().toBuilder().version(version).build());
    }

    /**
     * Starts one distro family for the given version using its default
     * download resolution rules.
     *
     * @param family distro family
     * @param version distro version
     * @return running server handle
     */
    public static ElasticServer start(DistroFamily family, String version) {
        return start(ElasticRunnerConfig.defaults(family).toBuilder().version(version).build());
    }

    /**
     * Starts Elasticsearch after transforming the default configuration.
     *
     * @param configurer function that derives a config from defaults
     * @return running server handle
     */
    public static ElasticServer start(UnaryOperator<ElasticRunnerConfig> configurer) {
        return start(configurer.apply(ElasticRunnerConfig.defaults()));
    }

    /**
     * Starts Elasticsearch from a builder-based configuration DSL.
     *
     * @param consumer config builder callback
     * @return running server handle
     */
    public static ElasticServer start(Consumer<ElasticRunnerConfig.Builder> consumer) {
        return start(ElasticRunnerConfig.from(consumer));
    }

    /**
     * Starts a server, executes the action, and always closes the server.
     *
     * @param config full server configuration
     * @param action callback invoked with the running server
     */
    public static void withServer(ElasticRunnerConfig config, Consumer<ElasticServer> action) {
        try (ElasticServer server = start(config)) {
            action.accept(server);
        }
    }

    /**
     * Starts a server, executes the function, returns its value, and always
     * closes the server.
     *
     * @param config full server configuration
     * @param action callback invoked with the running server
     * @param <T> return type
     * @return callback result
     */
    public static <T> T withServer(ElasticRunnerConfig config, Function<ElasticServer, T> action) {
        try (ElasticServer server = start(config)) {
            return action.apply(server);
        }
    }

    /**
     * Starts a server from a builder callback, executes the action, and closes
     * the server afterward.
     *
     * @param configurer config builder callback
     * @param action callback invoked with the running server
     */
    public static void withServer(Consumer<ElasticRunnerConfig.Builder> configurer,
                                  Consumer<ElasticServer> action) {
        withServer(ElasticRunnerConfig.from(configurer), action);
    }

    /**
     * Starts a server from a builder callback, executes the function, returns
     * its value, and closes the server afterward.
     *
     * @param configurer config builder callback
     * @param action callback invoked with the running server
     * @param <T> return type
     * @return callback result
     */
    public static <T> T withServer(Consumer<ElasticRunnerConfig.Builder> configurer,
                                   Function<ElasticServer, T> action) {
        return withServer(ElasticRunnerConfig.from(configurer), action);
    }

    /**
     * Starts a process-backed search server using the provided configuration.
     *
     * @param config immutable runner configuration
     * @return running server handle
     */
    public static ElasticServer start(ElasticRunnerConfig config) {
        Objects.requireNonNull(config, "config");
        Path zip = resolveDistroZipInternal(config);
        DistroFamily family = DistroFamily.infer(zip).orElse(config.family());

        String version = config.version() != null ? config.version() : DistroVersion.fromZip(zip);
        Path workDir = config.workDir().toAbsolutePath();
        Path versionDir = workDir.resolve(version);
        createDirs(versionDir);

        Path homeDir;
        try {
            homeDir = DistroLayout.prepare(zip, versionDir, family);
        } catch (IOException e) {
            throw new ElasticRunnerException("Failed to prepare " + family.displayName() + " distribution.", e);
        }

        Path dataDir = versionDir.resolve("data");
        Path logsDir = versionDir.resolve("logs");
        createDirs(dataDir);
        createDirs(logsDir);

        String httpPortSetting = resolveHttpPortSetting(config);

        Path configDir = homeDir.resolve("config");
        Path configFile = configDir.resolve(family.configFileName());
        writeConfig(config, family, httpPortSetting, dataDir, logsDir, configFile);

        if (!config.plugins().isEmpty()) {
            installPlugins(config.plugins(), homeDir, logsDir, config.quiet(), family);
        }

        Path logFile = logsDir.resolve("runner.log");
        Path pidFile = versionDir.resolve("es.pid");
        Path stateFile = versionDir.resolve("state.json");
        Process process = startProcess(homeDir, configDir, config, pidFile, family);
        Thread logThread = new Thread(new StreamGobbler(process.getInputStream(), logFile, config.quiet()),
                "es-runner-log");
        logThread.setDaemon(true);
        logThread.start();

        try {
            int actualPort = config.httpPort() > 0
                    ? config.httpPort()
                    : waitForHttpPortBinding(process, logFile, config.startupTimeout());
            Instant startTime = Instant.now();
            URI baseUri = URI.create("http://localhost:" + actualPort + "/");
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();

            waitForReady(httpClient, baseUri, config.startupTimeout(), process, logFile, family);

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

    /**
     * Resolves the ZIP archive for a config, downloading it first when needed.
     *
     * @param config immutable runner configuration
     * @return local ZIP archive path
     */
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
        DistroDescriptor descriptor = DistroDescriptor.forVersion(config.family(), config.version());
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

    static String resolveHttpPortSetting(ElasticRunnerConfig config) {
        if (config.httpPort() > 0) {
            return Integer.toString(config.httpPort());
        }
        if (config.portRangeStart() == config.portRangeEnd()) {
            return Integer.toString(config.portRangeStart());
        }
        return config.portRangeStart() + "-" + config.portRangeEnd();
    }

    private static void writeConfig(ElasticRunnerConfig config,
                                    DistroFamily family,
                                    String httpPortSetting,
                                    Path dataDir,
                                    Path logsDir,
                                    Path configFile) {
        Map<String, String> settings = new LinkedHashMap<>(config.settings());
        settings.putIfAbsent("cluster.name", config.clusterName());
        settings.put("path.data", dataDir.toString());
        settings.put("path.logs", logsDir.toString());
        settings.put("http.port", httpPortSetting);
        try {
            ConfigWriter.write(configFile, settings);
        } catch (IOException e) {
            throw new ElasticRunnerException("Failed to write " + family.configFileName(), e);
        }
    }

    private static void installPlugins(List<String> plugins,
                                       Path homeDir,
                                       Path logsDir,
                                       boolean quiet,
                                       DistroFamily family) {
        Path pluginScript = homeDir.resolve("bin").resolve(Os.executableName(family.pluginScriptBaseName()));
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
                        "es-runner-plugin-" + plugin);
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
                                        Path pidFile,
                                        DistroFamily family) {
        Path script = homeDir.resolve("bin").resolve(Os.executableName(family.launcherBaseName()));
        List<String> command = Os.commandFor(script);
        command = new java.util.ArrayList<>(command);
        command.add("-p");
        command.add(pidFile.toString());
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(homeDir.toFile());
        builder.redirectErrorStream(true);
        Map<String, String> env = builder.environment();
        env.put(family.pathConfEnvVar(), configDir.toString());
        env.put(family.javaOptsEnvVar(), "-Xms" + config.heap() + " -Xmx" + config.heap());
        try {
            return builder.start();
        } catch (IOException e) {
            throw new ElasticRunnerException("Failed to start " + family.displayName() + " process.", e);
        }
    }

    private static void waitForReady(HttpClient httpClient,
                                     URI baseUri,
                                     Duration timeout,
                                     Process process,
                                     Path logFile,
                                     DistroFamily family) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                String logTail = LogTail.read(logFile, 20);
                throw new ElasticRunnerException(family.displayName() + " process exited early. " + logTail);
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
                throw new ElasticRunnerException("Interrupted while waiting for " + family.displayName() + ".", e);
            } catch (IOException ignored) {
                // Retry until timeout.
            }
            sleep(500);
        }
        String logTail = LogTail.read(logFile, 20);
        throw new ElasticRunnerException("Timed out waiting for " + family.displayName() + ". " + logTail);
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

    private static int waitForHttpPortBinding(Process process, Path logFile, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                String logTail = LogTail.read(logFile, 20);
                throw new ElasticRunnerException("Search server exited unexpectedly before binding HTTP port: " + logTail);
            }
            try {
                if (Files.exists(logFile)) {
                    OptionalInt port = findHttpPublishPort(Files.readAllLines(logFile));
                    if (port.isPresent()) {
                        return port.getAsInt();
                    }
                }
            } catch (IOException ignored) {
            }
            sleep(500);
        }
        String logTail = LogTail.read(logFile, 20);
        throw new ElasticRunnerException("Timed out waiting for HTTP port bind: " + logTail);
    }

    static OptionalInt findHttpPublishPort(List<String> lines) {
        java.util.regex.Pattern portPattern = java.util.regex.Pattern.compile("publish_address \\{[^}]*:(\\d+)\\}");
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (!looksLikeHttpPublishAddress(line)) {
                continue;
            }
            java.util.regex.Matcher matcher = portPattern.matcher(line);
            if (matcher.find()) {
                return OptionalInt.of(Integer.parseInt(matcher.group(1)));
            }
        }
        return OptionalInt.empty();
    }

    private static boolean looksLikeHttpPublishAddress(String line) {
        if (!line.contains("publish_address")) {
            return false;
        }
        if (line.contains("HttpServerTransport")) {
            return true;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("http") && lower.contains("bound_addresses");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
