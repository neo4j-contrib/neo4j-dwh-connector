package org.neo4j.dwh.connector

import org.apache.commons.cli.HelpFormatter
import org.apache.commons.lang3.StringUtils
import org.apache.spark.sql
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.neo4j.dwh.connector.domain.{JobConfig, Source, Target}
import org.neo4j.dwh.connector.generator.JobConfigGenerator
import org.neo4j.dwh.connector.utils.CliUtils.JsonType
import org.neo4j.dwh.connector.utils.{CliUtils, JobConfigUtils, Utils}

import java.net.{MalformedURLException, URL}
import java.util
import java.util.Locale

/**
 * @author Andrea Santurbano
 */
object Neo4jDWHConnector {

  def main(args: Array[String]) {
    val cli = CliUtils.parseArgs(args)
    if (CliUtils.hasHelp(cli)) {
      val fmt = new HelpFormatter()
      fmt.printHelp(CliUtils.helpText, CliUtils.options())
      return
    }
    CliUtils.validateCli(cli)
    val pathAsString = cli.getOptionValue("p")
    val filePath = try {
      new URL(pathAsString)
    } catch {
      case mue: MalformedURLException if mue.getMessage.contains("no protocol") => new URL(s"file:$pathAsString")
      case t: Throwable => throw t
    }
    val isGenerateConfig = cli.hasOption("c")
    if (isGenerateConfig) {
      new JobConfigGenerator(cli).generate()
    } else {
      val jobs = JsonType.withName(cli
          .getOptionValue("ft", CliUtils.JsonType.SINGLE.toString.toUpperCase(Locale.ENGLISH))
          .toUpperCase(Locale.ENGLISH)) match {
        case JsonType.ARRAY => JobConfig.fromSeq(filePath)
        case JsonType.SINGLE => Seq(JobConfig.from(filePath))
      }
      jobs.foreach(job => new Neo4jDWHConnector(job).run(true))
    }
  }
}

class Neo4jDWHConnector(session: SparkSession, job: JobConfig) {

  def this(jobConfig: JobConfig) = this(JobConfigUtils.toSparkSession(jobConfig), jobConfig)
  def this(session: SparkSession, jobConfigMap: util.Map[String, AnyRef]) = this(session, JobConfig.from(jobConfigMap))

  def run(closeSession: Boolean = false): Unit = try {
    val dataFrame = read(job.source, session)
    write(job.target, dataFrame)
  } finally {
    if (closeSession) {
      session.close()
    }
  }

  private def read(source: Source, spark: SparkSession): DataFrame = {
    var dataFrame = spark.read.format(source.format)
      .options(Utils.enrichMap(source.options))
      .load()
    if (StringUtils.isNotBlank(source.where)) {
      dataFrame = dataFrame.where(source.where)
    }
    if (!source.columns.isEmpty) {
      dataFrame = dataFrame.selectExpr(source.columns.map(_.toString).toArray : _*)
    }

    if (source.printSchema) {
      dataFrame.printSchema()
    }

    if (source.limit > 0) {
      dataFrame = dataFrame.limit(source.limit)
    }

    if (source.show > 0) {
      dataFrame.show(source.show)
    }

    if (source.partition.number > 0) {
      dataFrame = if (StringUtils.isBlank(source.partition.by)) {
        dataFrame.repartition(source.partition.number)
      } else {
        dataFrame.repartition(source.partition.number, new sql.Column(source.partition.by))
      }
    }
    dataFrame
  }

  private def write(target: Target, df: DataFrame): Unit = {
    var dfWriter = df.write.format(target.format)
      .options(Utils.enrichMap(target.options))
    if (StringUtils.isNotBlank(target.mode)) {
      dfWriter = dfWriter.mode(target.mode)
    }
    dfWriter.save()
  }
}
