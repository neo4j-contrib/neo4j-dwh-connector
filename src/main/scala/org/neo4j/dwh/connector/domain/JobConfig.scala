package org.neo4j.dwh.connector.domain

import com.fasterxml.jackson.core.`type`.TypeReference
import org.apache.commons.lang3.StringUtils
import org.neo4j.dwh.connector.utils.JSONUtils

import java.io.File
import java.net.{URI, URL}
import java.util

case class Column(name: String, alias: String = "") {
  override def toString: String = if (StringUtils.isBlank(alias)) name else s"$name AS $alias"
}

case class Partition(number: Int = 0, by: String = "")

case class Source(format: String,
                  options: Map[String, String],
                  columns: Seq[Column] = Seq.empty,
                  where: String = "",
                  printSchema: Boolean,
                  limit: Int = -1,
                  show: Int = -1,
                  partition: Partition = Partition())

case class Target(format: String,
                  options: Map[String, String],
                  mode: String)

case class JobConfig(name: String = "Neo4j DWH Connector Job",
                     master: String = "",
                     conf: Map[String, String] = Map.empty,
                     hadoopConfiguration: Map[String, String] = Map.empty,
                     source: Source,
                     target: Target)

object JobConfig {

  def from(data: AnyRef): JobConfig = data match {
    case json: String => JSONUtils.mapper.readValue(json, classOf[JobConfig])
    case file: File => JSONUtils.mapper.readValue(file, classOf[JobConfig])
    case uri: URI => JSONUtils.mapper.readValue(uri.toURL, classOf[JobConfig])
    case url: URL => JSONUtils.mapper.readValue(url, classOf[JobConfig])
    case map: util.Map[_, _] => JSONUtils.mapper.convertValue(map, classOf[JobConfig])
    case _ => throw new IllegalArgumentException("Supported input types are String and File")
  }

  def fromSeq(data: AnyRef): Seq[JobConfig] = data match {
    case json: String => JSONUtils.mapper.readValue(json, new TypeReference[Seq[JobConfig]] {})
    case file: File => JSONUtils.mapper.readValue(file, new TypeReference[Seq[JobConfig]] {})
    case uri: URI => JSONUtils.mapper.readValue(uri.toURL, new TypeReference[Seq[JobConfig]] {})
    case url: URL => JSONUtils.mapper.readValue(url, new TypeReference[Seq[JobConfig]] {})
    case list: util.List[_] => JSONUtils.mapper.convertValue(list, new TypeReference[Seq[JobConfig]] {})
    case _ => throw new IllegalArgumentException("Supported input types are String and File")
  }
}
