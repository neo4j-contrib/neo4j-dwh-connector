package org.neo4j.dwh.connector.utils

import org.apache.commons.io.FileUtils

import java.io.File
import java.nio.charset.Charset
import scala.util.{Properties, Try}

object Utils {

  private val envPattern = """\$\{env:(.*)\}""".r
  private val filePattern = """\$\{file:(.*)\}""".r

  def enrichMap(map: Map[String, String]): Map[String, String] = map
    .map(t => (t._1, Try((envPattern findAllIn t._2).group(1))
      .map(Properties.envOrElse(_, t._2))
      .orElse(
        Try((filePattern findAllIn t._2).group(1))
          .map(path => FileUtils.readFileToString(new File(path), Charset.forName("UTF-8")))
      )
      .getOrElse(t._2)))

}
