package com.elastic.runner

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.nio.file.Files
import java.time.Duration

class ReadmeScala3ExampleTest:

  @Test
  def readmeScala3ExampleWorks(): Unit =
    val version = sys.env.getOrElse("ES_VERSION", "9.3.1")
    val major = version.takeWhile(_ != '.')
    val workDir = Files.createTempDirectory("es-runner-readme-scala3-")
    val config = IntegrationTestSupport.configFromExample(
      version,
      workDir,
      "readme-scala3",
      Duration.ofSeconds(120),
      true,
      builder => builder.setting("discovery.type", "single-node")
    )

    val server = ElasticRunner.start(config)
    try
      val client = server.client()
      assertTrue(client.clusterHealth().status() != "")
      assertTrue(client.version().startsWith(s"$major."))
    finally
      server.close()
