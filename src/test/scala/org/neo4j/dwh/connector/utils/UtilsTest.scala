package org.neo4j.dwh.connector.utils

import org.apache.commons.lang3.StringUtils
import org.junit.Assert.assertEquals
import org.junit.{Assume, Test}

import java.net.URL
import scala.util.Properties

class UtilsTest {

  private val queryUrl: URL = Thread
    .currentThread
    .getContextClassLoader
    .getResource("query.cyp")

  private val source = scala.io.Source
    .fromFile(queryUrl.toURI)
  private val queryFile: String = try {
    source
      .getLines()
      .mkString("\n")
  } finally {
    source.close()
  }

  @Test
  def shouldReturnMapWithEnvAndFileContent(): Unit = {
    Assume.assumeTrue(StringUtils.isNotBlank(Properties.envOrElse("MY_ENV", "")))
    val sourceMap = Map("foo" -> "bar",
      "withEnv" -> "${env:MY_ENV}",
      "noEnv" -> "${env:NO_ENV}",
      "withFile" -> s"$${$queryUrl}",
      "noFile" -> "${file:/foo/bar.cyp}")
    val expected = Map("foo" -> "bar",
      "withEnv" -> "MY_ENV_value",
      "noEnv" -> "${env:NO_ENV}",
      "withFile" -> queryFile,
      "noFile" -> "${file:/foo/bar.cyp}")
    val actual = Utils.enrichMap(sourceMap)
    assertEquals(expected, actual)
  }
}
