package org.neo4j.dwh.connector.utils

import org.apache.commons.lang3.StringUtils
import org.apache.spark.sql.SparkSession
import org.neo4j.dwh.connector.domain.JobConfig

object JobConfigUtils {

  def toSparkSession(jobConfig: JobConfig): SparkSession = {
    val sessionBuilder = SparkSession.builder
      .appName(jobConfig.name)

    if (StringUtils.isNotBlank(jobConfig.master)) {
      sessionBuilder.master(jobConfig.master)
    }

    jobConfig.conf.foreach(t => sessionBuilder.config(t._1, t._2))

    val session = sessionBuilder.getOrCreate()
    jobConfig.hadoopConfiguration.foreach(t => session.sparkContext.hadoopConfiguration.set(t._1, t._2))

    session
  }
}
