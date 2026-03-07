package com.elastic.runner

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.nio.file.Files
import java.time.Duration

class ReadmeScalaExampleTest {

  @Test
  def readmeScalaExampleWorks(): Unit = {
    val version = sys.env.getOrElse("ES_VERSION", "9.2.4")
    val workDir = Files.createTempDirectory("es-runner-readme-scala-")
    val config = IntegrationTestSupport.configFromExample(
      version,
      workDir,
      "readme-scala",
      Duration.ofSeconds(120),
      true,
      builder => builder.setting("discovery.type", "single-node")
    )

    val server = ElasticRunner.start(config)
    try {
      val client = server.client()
      assertTrue(client.clusterHealth().contains("\"status\""))
      assertTrue(client.version().startsWith(version.takeWhile(_ != '.') + "."))
    } finally {
      server.close()
    }
  }
}
