package org.neo4j.dwh.connector.generator

import org.apache.commons.cli.CommandLine
import org.neo4j.dwh.connector.domain.JobConfig
import org.neo4j.dwh.connector.utils.{DatasourceOptions, JSONUtils}

import java.io.File

class JobConfigGenerator(private val cli: CommandLine) {

  def generate(): Unit = {
    val source = DatasourceOptions
      .withNameIgnoreCase(cli.getOptionValue("s"))

    val target = DatasourceOptions
      .withNameIgnoreCase(cli.getOptionValue("t"))

    val deps = (source.deps ++ target.deps)
      .map(dep => s" - `$dep`")
      .mkString("\n")

    val jobConfig = JobConfig(
      name =
        s"""
          |<This is a generated Configuration, please fill all the field accordingly>
          |In order to work the following dependencies are required:
          |$deps
          |""".stripMargin,
      source = source.toSource(),
      target = target.toTarget(),
      conf = (source.conf ++ target.conf),
      hadoopConfiguration = (source.hadoopConf ++ target.hadoopConf)
    )

    JSONUtils.mapper
      .writerWithDefaultPrettyPrinter
      .writeValue(new File(cli.getOptionValue("p")), jobConfig)
  }
}
