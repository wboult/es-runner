package io.github.wboult.esrunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

final class DistroDownloader {
    private DistroDownloader() {
    }

    static void download(URI uri, Path target) {
        Path temp = target.resolveSibling(target.getFileName() + ".partial");
        try {
            Files.createDirectories(target.getParent());
            switch (normalizedScheme(uri)) {
                case "http":
                case "https":
                    downloadHttp(uri, temp);
                    break;
                case "file":
                    Files.copy(Path.of(uri), temp, StandardCopyOption.REPLACE_EXISTING);
                    break;
                case "s3":
                case "gs":
                case "az":
                    downloadWithCommand(uri, temp, System.getenv());
                    break;
                default:
                    throw new ElasticRunnerException("Unsupported download URI scheme: " + uri);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
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

    static List<List<String>> commandCandidates(URI uri, Path target, Map<String, String> env) {
        String scheme = normalizedScheme(uri);
        return switch (scheme) {
            case "s3" -> prefixedCommands(
                    externalCommandNames("aws"),
                    List.of("s3", "cp", uri.toString(), target.toString(), "--only-show-errors")
            );
            case "gs" -> concat(
                    prefixedCommands(externalCommandNames("gcloud"),
                            List.of("storage", "cp", uri.toString(), target.toString())),
                    prefixedCommands(externalCommandNames("gsutil"),
                            List.of("-q", "cp", uri.toString(), target.toString()))
            );
            case "az" -> azureCommandCandidates(uri, target, env);
            default -> List.of();
        };
    }

    private static void downloadHttp(URI uri, Path target) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ElasticRunnerException("Failed to download distro: " + uri + " (HTTP " + response.statusCode() + ")");
        }
    }

    private static void downloadWithCommand(URI uri, Path target, Map<String, String> env)
            throws IOException, InterruptedException {
        List<List<String>> candidates = commandCandidates(uri, target, env);
        IOException missing = null;
        for (List<String> command : candidates) {
            try {
                runCommand(command);
                return;
            } catch (IOException e) {
                if (isMissingCommand(e)) {
                    if (missing == null) {
                        missing = e;
                    }
                    continue;
                }
                throw e;
            }
        }
        throw new ElasticRunnerException("No supported CLI found for " + uri.getScheme()
                + " downloads. Tried: " + commandNames(candidates), missing);
    }

    private static void runCommand(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = process.getInputStream()) {
            input.transferTo(output);
        }
        boolean finished = process.waitFor(10, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new ElasticRunnerException("Download command timed out: " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            String message = output.toString().trim();
            throw new ElasticRunnerException("Download command failed: " + String.join(" ", command)
                    + (message.isEmpty() ? "" : System.lineSeparator() + message));
        }
    }

    private static List<List<String>> azureCommandCandidates(URI uri, Path target, Map<String, String> env) {
        AzureBlobLocation blob = AzureBlobLocation.from(uri);
        List<List<String>> commands = new ArrayList<>(prefixedCommands(
                externalCommandNames("azcopy"),
                List.of(
                        "copy",
                        blob.toHttpsUri().toString(),
                        target.toString(),
                        "--overwrite=true"
                )
        ));

        List<String> azArgs = new ArrayList<>(List.of(
                "storage",
                "blob",
                "download",
                "--account-name",
                blob.accountName(),
                "--container-name",
                blob.containerName(),
                "--name",
                blob.blobName(),
                "--file",
                target.toString(),
                "--no-progress"
        ));
        if (!hasAzureStorageCredentialEnv(env)) {
            azArgs.add("--auth-mode");
            azArgs.add("login");
        }
        commands.addAll(prefixedCommands(externalCommandNames("az"), azArgs));
        return List.copyOf(commands);
    }

    private static boolean hasAzureStorageCredentialEnv(Map<String, String> env) {
        return hasValue(env, "AZURE_STORAGE_CONNECTION_STRING")
                || hasValue(env, "AZURE_STORAGE_KEY")
                || hasValue(env, "AZURE_STORAGE_SAS_TOKEN");
    }

    private static boolean hasValue(Map<String, String> env, String key) {
        String value = env.get(key);
        return value != null && !value.isBlank();
    }

    private static boolean isMissingCommand(IOException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("error=2")
                || normalized.contains("cannot find the file")
                || normalized.contains("no such file or directory");
    }

    private static String commandNames(List<List<String>> candidates) {
        return candidates.stream()
                .map(command -> command.isEmpty() ? "<empty>" : command.get(0))
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("<none>");
    }

    private static List<String> externalCommandNames(String base) {
        if (!Os.isWindows()) {
            return List.of(base);
        }
        return List.of(base + ".cmd", base + ".exe", base);
    }

    private static List<List<String>> prefixedCommands(List<String> commands, List<String> args) {
        List<List<String>> expanded = new ArrayList<>();
        for (String command : commands) {
            List<String> full = new ArrayList<>();
            full.add(command);
            full.addAll(args);
            expanded.add(List.copyOf(full));
        }
        return List.copyOf(expanded);
    }

    private static List<List<String>> concat(List<List<String>> left, List<List<String>> right) {
        List<List<String>> combined = new ArrayList<>(left.size() + right.size());
        combined.addAll(left);
        combined.addAll(right);
        return List.copyOf(combined);
    }

    private static String normalizedScheme(URI uri) {
        Objects.requireNonNull(uri, "uri");
        String scheme = uri.getScheme();
        if (scheme == null || scheme.isBlank()) {
            return "http";
        }
        return scheme.toLowerCase(Locale.ROOT);
    }

    static final class AzureBlobLocation {
        private final String accountName;
        private final String containerName;
        private final String blobName;
        private final String query;

        private AzureBlobLocation(String accountName, String containerName, String blobName, String query) {
            this.accountName = accountName;
            this.containerName = containerName;
            this.blobName = blobName;
            this.query = query;
        }

        static AzureBlobLocation from(URI uri) {
            String account = uri.getHost();
            if (account == null || account.isBlank()) {
                throw new ElasticRunnerException("Azure download URIs must be in the form az://account/container/path");
            }
            String path = uri.getPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                throw new ElasticRunnerException("Azure download URIs must include container and blob path: " + uri);
            }
            String normalized = path.startsWith("/") ? path.substring(1) : path;
            String[] parts = normalized.split("/", 2);
            if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new ElasticRunnerException("Azure download URIs must include container and blob path: " + uri);
            }
            return new AzureBlobLocation(account, parts[0], parts[1], uri.getRawQuery());
        }

        String accountName() {
            return accountName;
        }

        String containerName() {
            return containerName;
        }

        String blobName() {
            return blobName;
        }

        URI toHttpsUri() {
            try {
                return new URI(
                        "https",
                        accountName + ".blob.core.windows.net",
                        "/" + containerName + "/" + blobName,
                        query,
                        null
                );
            } catch (Exception e) {
                throw new ElasticRunnerException("Failed to build Azure blob HTTPS URI.", e);
            }
        }
    }
}
