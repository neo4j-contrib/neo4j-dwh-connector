package org.neo4j.dwh.connector.generator

import org.junit.{Assert, Test}
import org.neo4j.dwh.connector.domain.JobConfig
import org.neo4j.dwh.connector.utils.{CliUtils, JSONUtils}

import java.io.File
import scala.util.Properties

class JobConfigGeneratorTest {

  @Test
  def shouldCreateConfigStubFileFromSnowflakeToNeo4j(): Unit = {
    val filePath = Properties.propOrElse("java.io.tmpdir", "").concat("/config.stub.json")
    val cli = CliUtils.parseArgs(Array("-p", filePath, "-c", "-s", "Snowflake", "-t", "Neo4j"))
    new JobConfigGenerator(cli).generate()
    val actual = JSONUtils.mapper.readValue(new File(filePath), classOf[Map[String, Any]])
    val expected = JSONUtils.mapper.readValue(Thread.currentThread()
      .getContextClassLoader
      .getResourceAsStream("snowflake.to.neo4j.stub.json"), classOf[Map[String, Any]])
    Assert.assertEquals(expected, actual)
  }

}
