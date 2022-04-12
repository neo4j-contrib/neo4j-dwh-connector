package org.neo4j.dwh.connector.utils

import org.apache.commons.cli.{BasicParser, CommandLine, Options}

object CliUtils {
  object JsonType extends Enumeration {
    val SINGLE, ARRAY = Value
  }

  val helpText = """In case you're using it to generate the configuration stub:
                   |java -jar neo4j-dwh-connector-<version>.jar -c -s <Source Database> -t <Target Database> -p <Where to put the configuration json file>
                   |In case you're using it with Spark Submit (from $SPARK_HOME):
                   |./bin/spark-submit \
                   |  --class org.neo4j.dwh.connector.Neo4jDWHConnector \
                   |  --packages <required dependecies> \
                   |  --master spark://<SPARK_IP>:<SPARK_PORT> \
                   |  --deploy-mode cluster \
                   |  --supervise \
                   |  --executor-memory 20G \
                   |  --total-executor-cores 100 \
                   |  /path/to/neo4j-dwh-connector-<version>.jar \
                   |  -p /path/to/dwh_job_config.json
                   |""".stripMargin

  // if used in Databricks Job env don't work with DefaultParser
  // because I guess that for some reason they use an old version of apache CommonsCli
  // so we're using BasicParser in order to make it work
  def parseArgs(args: Array[String]): CommandLine = new BasicParser().parse(options(), args)

  def hasHelp(cli: CommandLine): Boolean = cli.hasOption("h")

  def options(): Options = {
    val supportedDataSource = DatasourceOptions
      .values
      .map(_.toString)
      .map(name => s" - `$name`")
      .mkString("\n")
    new Options()
      .addOption("p", "path", true, """If used in combination with the `c` option is the position
                                                 |where the configuration field will be saved, otherwise where
                                                 |it will be read in order to start the Spark Job.
                                                 |""".stripMargin)
      .addOption("c", "config", false, """Generates a configuration stub that can be used with the DWH connector.
                                         |You need to define -s and -t options in order to
                                         |specify which are the source and target data sources.
                                         |""".stripMargin)
      .addOption("s", "source", true, s"""In combination with -c, it generates a stub configuration with the selected source database.
                                         |Supported Data sources are:
                                         |$supportedDataSource
                                         |""".stripMargin)
      .addOption("t", "target", true, s"""In combination with -c, it generates a stub configuration with the selected target database.
                                         |Supported Data sources are:
                                         |$supportedDataSource
                                         |""".stripMargin)
      .addOption("ft", "file_type", true, s"""The config file type:
                                            |	- `${JsonType.SINGLE}` (default) means a single json
                                            |	- `${JsonType.ARRAY}` means that you're passing an array of json
                                            |""".stripMargin)
      .addOption("h","help", false, "Prints the help")
  }

  def validateCli(cli: CommandLine): Unit = {
    val hasPath = cli.hasOption("p")
    if (!hasPath) {
      throw new IllegalArgumentException("Option -p is required")
    }
    val isGenerateConfig = cli.hasOption("c")
    if (isGenerateConfig) {
      val hasGenerateTarget = cli.hasOption("t")
      val hasGenerateSource = cli.hasOption("s")
      if (!hasGenerateSource && !hasGenerateTarget) {
        throw new IllegalArgumentException("You must define `-t` and `-s` option in combination with `-c`")
      }
    }
  }
}
