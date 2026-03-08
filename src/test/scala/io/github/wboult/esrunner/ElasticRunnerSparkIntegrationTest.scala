package io.github.wboult.esrunner

import org.apache.spark.sql.SparkSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import java.nio.file.Files
import java.time.Duration

class ElasticRunnerSparkIntegrationTest {

  @Test
  def sparkSessionWritesToElasticsearch(): Unit = {
    val version = sys.env.getOrElse("ES_VERSION", "9.3.1")
    val workDir = Files.createTempDirectory("es-runner-spark-it-")
    val config = IntegrationTestSupport.config(
      version,
      workDir,
      "spark-it-cluster",
      "512m",
      Duration.ofSeconds(180),
      true
    )

    val total = 100000L
    val index = "spark-it"
    val bulkSize = 1000

    val server = ElasticRunner.start(config)
    try {
      val spark = SparkSession.builder()
        .appName("es-runner-spark-it")
        .master("local[1]")
        .config("spark.ui.enabled", "false")
        .config("spark.sql.shuffle.partitions", "1")
        .getOrCreate()
      try {
        val client = server.client()
        client.createIndex(index)

        val df = spark.range(total).toDF("id")
        df.rdd.foreachPartition { rows =>
          val localClient = client
          val builder = new StringBuilder()
          var count = 0
          rows.foreach { row =>
            val id = row.getLong(0)
            builder.append("{\"index\":{\"_index\":\"")
              .append(index)
              .append("\",\"_id\":\"")
              .append(id)
              .append("\"}}\n")
            builder.append("{\"id\":")
              .append(id)
              .append(",\"value\":\"v")
              .append(id)
              .append("\"}\n")
            count += 1
            if (count % bulkSize == 0) {
              localClient.bulk(builder.toString())
              builder.setLength(0)
            }
          }
          if (builder.length > 0) {
            localClient.bulk(builder.toString())
          }
        }

        client.refresh(index)
        val countValue = client.countValue(index)
        assertEquals(total, countValue)
      } finally {
        spark.stop()
      }
    } finally {
      server.close()
    }
  }
}
