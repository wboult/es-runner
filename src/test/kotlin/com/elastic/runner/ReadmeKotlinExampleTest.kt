package com.elastic.runner

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.Duration

class ReadmeKotlinExampleTest {

    @Test
    fun readmeKotlinExampleWorks() {
        val version = System.getenv().getOrDefault("ES_VERSION", "9.2.4")
        val workDir = Files.createTempDirectory("es-runner-readme-kotlin-")
        val config = IntegrationTestSupport.configFromExample(
            version,
            workDir,
            "readme-kotlin",
            Duration.ofSeconds(120),
            true
        ) { builder ->
            builder.setting("discovery.type", "single-node")
        }

        val server = ElasticRunner.start(config)
        try {
            val client = server.client()
            assertTrue(client.clusterHealth().contains("\"status\""))
            assertTrue(client.version().startsWith("9."))
        } finally {
            server.close()
        }
    }
}
