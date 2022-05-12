package org.neo4j.dwh.connector.utils

import org.apache.commons.io.FileUtils

import java.io.File
import java.nio.charset.Charset
import scala.util.matching.Regex
import scala.util.{Properties, Try}

object Utils {

  private val envPattern = """\$\{env:(.*)\}""".r
  private val filePattern = """\$\{file:(.*)\}""".r

  def enrichMap(map: Map[String, String]): Map[String, String] = map
    .map(t => (t._1, Try(findAllInRegex(envPattern, t._2))
      .map(Properties.envOrElse(_, t._2))
      .orElse(
        Try(findAllInRegex(filePattern, t._2))
          .map(path => FileUtils.readFileToString(new File(path), Charset.forName("UTF-8")))
      )
      .getOrElse(t._2)))

  private def findAllInRegex(r: Regex, str: String): String = {
    // this is a workaround as the same regexp works in Scala 2.12 and 2.13 but not in 2.11
    if (Properties.versionString.startsWith("version 2.11") && str.matches(r.regex)) {
      val splits = r.regex.split("""\(\.\*\)""")
      str.replaceAll(splits(0), "")
        .replaceAll(splits(1), "")
    } else {
      (r findAllIn str).group(1)
    }
  }

}
