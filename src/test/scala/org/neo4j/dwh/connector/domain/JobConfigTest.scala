package org.neo4j.dwh.connector.domain

import org.apache.spark.sql.SaveMode
import org.junit.Assert.assertEquals
import org.junit.Test

class JobConfigTest {

  @Test
  def shouldParseTheJsonIntoJobConfig(): Unit = {
    val jsonConfig =
      """
        |{
        |	"name": "Create Persons from Snowflake to Neo4j",
        |	"master": "local",
        |	"_comment": "The `conf` field will add configuration via spark.conf.set",
        |	"conf": {
        |		"<field>": "<value>"
        |	},
        |	"_comment": "The `hadoopConfiguration` field will add configuration via spark.hadoopConfiguration().set",
        |	"hadoopConfiguration": {
        |		"<fieldHadoop>": "<valueHadoop>"
        |	},
        |	"_comment": "The `source` field is a general field that manages the source database. Is basically where we read the data",
        |	"source": {
        |		"_comment": "The `format` field manages the connector datasource",
        |		"format": "net.snowflake.spark.snowflake",
        |		"_comment": "The `options` field manages the connector datasource options, which are specific for each datasource",
        |		"options": {
        |			"sfURL": "<account_identifier>.snowflakecomputing.com",
        |			"sfUser": "<user_name>",
        |			"sfPassword": "<password>",
        |			"sfDatabase": "<database>",
        |			"sfSchema": "<schema>",
        |			"sfWarehouse": "<warehouse>",
        |     "dbtable": "<snowflake-table>"
        |		},
        |		"_comment": "The `columns` field manages projection of the dataframe, `name` is the column name, `alias` is the name that you want to give (not mandatory)",
        |		"columns": [
        |     {
        |				"name": "person_id",
        |				"alias": "id"
        |			},
        |			{
        |				"name": "person_name",
        |				"alias": "name"
        |			}
        |		],
        |		"_comment": "The `where` field manages filters on the datasource (not mandatory)",
        |		"where": "person_surname = 'Santurbano'",
        |		"_comment": "The `partition` field repartition the source dataframe this can be useful when you're ingesting relationships into Neo4j",
        |		"partition": {
        |     "number": 5,
        |     "by": "foo"
        |   }
        |	},
        |	"_comment": "The `target` field is a general field that manages the target database. Is basically where we write the data that has been read in the field `source`",
        |	"target": {
        |		"_comment": "The `format` field manages the connector datasource",
        |		"format": "org.neo4j.spark.DataSource",
        |		"_comment": "The `mode` is the save mode of the writing connector",
        |		"mode": "Overwrite",
        |		"_comment": "The `options` field manages the connector datasource options, which are specific for each datasource",
        |		"options": {
        |			"labels": ":Person:Customer",
        |     "node.keys": "id"
        |		}
        |	}
        |}
        |""".stripMargin

    val jobConfig = JobConfig.from(jsonConfig)

    assertEquals("Create Persons from Snowflake to Neo4j", jobConfig.name)
    assertEquals("local", jobConfig.master)
    assertEquals(Map("<field>" -> "<value>"), jobConfig.conf)
    assertEquals(Map("<fieldHadoop>" -> "<valueHadoop>"), jobConfig.hadoopConfiguration)

    assertEquals("net.snowflake.spark.snowflake", jobConfig.source.format)
    assertEquals(Map("sfURL" -> "<account_identifier>.snowflakecomputing.com",
      "sfUser" -> "<user_name>",
      "sfPassword" -> "<password>",
      "sfDatabase" -> "<database>",
      "sfSchema" -> "<schema>",
      "sfWarehouse" -> "<warehouse>",
      "dbtable" -> "<snowflake-table>"), jobConfig.source.options)
    assertEquals(Seq(Column("person_id", "id"), Column("person_name", "name")), jobConfig.source.columns)
    assertEquals("person_surname = 'Santurbano'", jobConfig.source.where)
    assertEquals(Partition(5, "foo"), jobConfig.source.partition)

    assertEquals("org.neo4j.spark.DataSource", jobConfig.target.format)
    assertEquals(SaveMode.Overwrite.toString, jobConfig.target.mode)
    assertEquals(Map("labels" -> ":Person:Customer", "node.keys" -> "id"), jobConfig.target.options)
  }
}
