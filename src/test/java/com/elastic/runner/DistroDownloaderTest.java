package com.elastic.runner;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistroDownloaderTest {

    @Test
    void buildsAwsCliCommandForS3Downloads() {
        List<List<String>> commands = DistroDownloader.commandCandidates(
                URI.create("s3://elastic-mirror/releases/elasticsearch.zip"),
                Path.of("target.zip"),
                Map.of()
        );

        assertTrue(commands.stream().anyMatch(command ->
                command.get(0).startsWith("aws")
                        && command.contains("s3")
                        && command.contains("cp")
                        && command.contains("s3://elastic-mirror/releases/elasticsearch.zip")
        ));
    }

    @Test
    void buildsGcsCommandsWithGcloudThenGsutilFallback() {
        List<List<String>> commands = DistroDownloader.commandCandidates(
                URI.create("gs://elastic-mirror/releases/elasticsearch.zip"),
                Path.of("target.zip"),
                Map.of()
        );

        assertTrue(commands.stream().anyMatch(command -> command.get(0).startsWith("gcloud")));
        assertTrue(commands.stream().anyMatch(command -> command.get(0).startsWith("gsutil")));
    }

    @Test
    void buildsAzureCommandsWithAzcopyAndLoginFallback() {
        List<List<String>> commands = DistroDownloader.commandCandidates(
                URI.create("az://acct123/releases/elasticsearch.zip"),
                Path.of("target.zip"),
                Map.of()
        );

        assertTrue(commands.stream().anyMatch(command ->
                command.get(0).startsWith("azcopy")
                        && command.contains("https://acct123.blob.core.windows.net/releases/elasticsearch.zip")
        ));
        assertTrue(commands.stream().anyMatch(command ->
                command.get(0).startsWith("az") && command.contains("--auth-mode") && command.contains("login")
        ));
    }

    @Test
    void skipsAzureLoginModeWhenStorageCredentialEnvironmentIsPresent() {
        List<List<String>> commands = DistroDownloader.commandCandidates(
                URI.create("az://acct123/releases/elasticsearch.zip"),
                Path.of("target.zip"),
                Map.of("AZURE_STORAGE_CONNECTION_STRING", "UseDevelopmentStorage=true")
        );

        assertTrue(commands.stream().anyMatch(command ->
                command.get(0).startsWith("az") && command.stream().noneMatch("--auth-mode"::equals)
        ));
    }
}
