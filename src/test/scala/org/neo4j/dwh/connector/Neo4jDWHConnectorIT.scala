package org.neo4j.dwh.connector

import org.apache.commons.io.FileUtils
import org.apache.spark.sql.SparkSession
import org.junit.{AfterClass, Assert, Assume, BeforeClass, Test}
import org.neo4j.driver.GraphDatabase
import org.neo4j.dwh.connector.Neo4jDWHConnectorIT.neo4jContainer
import org.neo4j.dwh.connector.domain.JobConfig
import org.testcontainers.containers.Neo4jContainer
import org.testcontainers.utility.DockerImageName

import java.io.File
import java.nio.charset.Charset
import scala.collection.JavaConverters._
import scala.util.Properties

object Neo4jDWHConnectorIT {
  private val properties = new java.util.Properties()
  properties.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("neo4j-dwh-connector.properties"))

  val neo4jContainer = new Neo4jContainer(DockerImageName.parse(s"neo4j:${properties.getProperty("neo4j.version")}"))
    .withNeo4jConfig("dbms.security.auth_enabled", "false")
    .asInstanceOf[Neo4jContainer[_]]

  @BeforeClass
  def setUpContainer(): Unit = {
    neo4jContainer.start()
  }

  @AfterClass
  def teardownContainer(): Unit = {
    neo4jContainer.stop()
  }
}

class Neo4jDWHConnectorIT {

  private def createPersons(numPersons: Int) = {
    val driver = GraphDatabase.driver(neo4jContainer.getBoltUrl)
    val neo4jSession = driver.session()
    try {
      neo4jSession.run(
        """UNWIND RANGE(1, $numPersons) AS ID
          |MERGE (p:Person:Customer{id: ID, name: 'Name ' + ID, surname: 'Surname ' + ID, age: 10 + ID})
          |RETURN count(p) AS count
          |""".stripMargin, Map[String, AnyRef]("numPersons" -> numPersons.asInstanceOf[AnyRef]).asJava)
        .consume()
    } finally {
      neo4jSession.close()
      driver.close()
    }
  }

  @Test
  def shouldImportCSVIntoNeo4j(): Unit = {
    val jsonConfig =
      s"""
        |{
        |  "name": "Create Persons from CSV to Neo4j",
        |  "master": "local",
        |  "_comment": "The `source` field is a general field that manages the source database. Is basically where we read the data",
        |  "source": {
        |    "_comment": "The `format` field manages the connector datasource",
        |    "format": "csv",
        |    "_comment": "The `options` field manages the connector datasource options, which are specific for each datasource",
        |    "options": {
        |      "header": "true",
        |      "path": "${Thread
                  .currentThread
                  .getContextClassLoader
                  .getResource("persons.csv")
                  .getPath}"
        |    },
        |    "_comment": "The `columns` field manages projection of the dataframe, `name` is the column name, `alias` is the name that you want to give (not mandatory)",
        |    "columns": [
        |      {
        |        "name": "person_id",
        |        "alias": "id"
        |      },
        |      {
        |        "name": "person_name",
        |        "alias": "name"
        |      }
        |    ],
        |    "_comment": "The `where` field manages filters on the datasource (not mandatory)",
        |    "where": "person_surname = 'Santurbano'"
        |  },
        |  "_comment": "The `target` field is a general field that manages the target database. Is basically where we write the data that has been read in the field `source`",
        |  "target": {
        |    "_comment": "The `format` field manages the connector datasource",
        |    "format": "org.neo4j.spark.DataSource",
        |    "_comment": "The `mode` is the save mode of the writing connector",
        |    "mode": "Overwrite",
        |    "_comment": "The `options` field manages the connector datasource options, which are specific for each datasource",
        |    "options": {
        |      "labels": ":Person:Customer",
        |      "url": "${neo4jContainer.getBoltUrl}",
        |      "node.keys": "id"
        |    }
        |  }
        |}
        |""".stripMargin

    runJob(jsonConfig)
    val driver = GraphDatabase.driver(neo4jContainer.getBoltUrl)
    val neo4jSession = driver.session()
    try {
      val count = neo4jSession.run(
        """
          |MATCH (p:Person:Customer)
          |WHERE p.name IN ['Andrea', 'Federico']
          |RETURN count(p) AS count
          |""".stripMargin)
        .single()
        .get(0)
        .asLong()
      Assert.assertEquals(2L, count)
    } finally {
      neo4jSession.close()
      driver.close()
    }
  }

  @Test
  def shouldWriteCSVFromNeo4j(): Unit = {
    val csvPath = Properties.propOrElse("java.io.tmpdir", "").concat("/from-neo4j")

    val driver = GraphDatabase.driver(neo4jContainer.getBoltUrl)
    val neo4jSession = driver.session()
    try {
      neo4jSession.run(
        """
          |UNWIND range(1, 2) AS id
          |MERGE (p:Person:Customer {id: id, name: 'Name For Id ' + id})
          |RETURN p
          |""".stripMargin)
        .consume()
    } finally {
      neo4jSession.close()
      driver.close()
    }

    val jsonConfig =
      s"""
         |{
         |  "name": "Create Persons from CSV to Neo4j",
         |  "master": "local",
         |  "_comment": "The `source` field is a general field that manages the source database. Is basically where we read the data",
         |  "source": {
         |    "_comment": "The `format` field manages the connector datasource",
         |    "format": "org.neo4j.spark.DataSource",
         |    "_comment": "The `options` field manages the connector datasource options, which are specific for each datasource",
         |    "options": {
         |      "labels": ":Person:Customer",
         |      "url": "${neo4jContainer.getBoltUrl}"
         |    },
         |    "_comment": "The `columns` field manages projection of the dataframe, `name` is the column name, `alias` is the name that you want to give (not mandatory)",
         |    "columns": [
         |      {
         |        "name": "id"
         |      },
         |      {
         |        "name": "name"
         |      }
         |    ]
         |  },
         |  "_comment": "The `target` field is a general field that manages the target database. Is basically where we write the data that has been read in the field `source`",
         |  "target": {
         |    "_comment": "The `format` field manages the connector datasource",
         |    "format": "csv",
         |    "_comment": "The `mode` is the save mode of the writing connector",
         |    "mode": "Overwrite",
         |    "_comment": "The `options` field manages the connector datasource options, which are specific for each datasource",
         |    "options": {
         |      "header": "true",
         |      "path": "$csvPath"
         |    }
         |  }
         |}
         |""".stripMargin

    runJob(jsonConfig)

    val csvFile = new File(csvPath)
      .listFiles()
      .filter(_.isFile)
      .filter(_.getName.endsWith("csv"))(0)

    val actual = FileUtils.readFileToString(csvFile, Charset.forName("UTF-8"))
    val expected =
      """id,name
        |1,Name For Id 1
        |2,Name For Id 2
        |""".stripMargin
    Assert.assertEquals(expected, actual)
  }

  @Test
  def shouldImportSnowflakeIntoNeo4j(): Unit = {
    val snowflakeschema = Properties.envOrNone("SNOWFLAKE_SCHEMA")
    Assume.assumeFalse(snowflakeschema.isEmpty)
    val snowflakeuser = Properties.envOrNone("SNOWFLAKE_USER")
    Assume.assumeFalse(snowflakeuser.isEmpty)
    val snowflakepassword = Properties.envOrNone("SNOWFLAKE_PASSWORD")
    Assume.assumeFalse(snowflakepassword.isEmpty)
    val snowflakedatabase = Properties.envOrNone("SNOWFLAKE_DATABASE")
    Assume.assumeFalse(snowflakedatabase.isEmpty)
    val snowflakeurl = Properties.envOrNone("SNOWFLAKE_URL")
    Assume.assumeFalse(snowflakeurl.isEmpty)
    val snowflaketable = Properties.envOrNone("SNOWFLAKE_TABLE")
    Assume.assumeFalse(snowflaketable.isEmpty)
    val jsonConfig =
      s"""
         |{
         |  "name": "Create Customers from Snowflake to Neo4j",
         |  "master": "local",
         |  "_comment": "The `source` field is a general field that manages the source database. Is basically where we read the data",
         |  "source": {
         |    "_comment": "The `format` field manages the connector datasource",
         |    "format" : "snowflake",
         |    "_comment": "The `options` field manages the connector datasource options, which are specific for each datasource",
         |    "options" : {
         |      "sfSchema" : "${snowflakeschema.get}",
         |      "sfPassword" : "${snowflakepassword.get}",
         |      "sfUser" : "${snowflakeuser.get}",
         |      "dbtable" : "${snowflaketable.get}",
         |      "sfDatabase" : "${snowflakedatabase.get}",
         |      "sfURL" : "${snowflakeurl.get}"
         |    },
         |    "_comment": "The `where` field manages filters on the datasource (not mandatory)",
         |    "where": "C_CUSTKEY <= 10",
         |    "_comments": "The `columns` field manages the projection of Dataframe columns",
         |    "columns": [
         |      { "name": "CAST(C_ACCTBAL AS DOUBLE)", "alias": "C_ACCTBAL" },
         |      { "name": "C_ADDRESS" },
         |      { "name": "C_COMMENT" },
         |      { "name": "CAST(C_CUSTKEY AS LONG)", "alias": "C_CUSTKEY" },
         |      { "name": "C_MKTSEGMENT" },
         |      { "name": "C_NAME" },
         |      { "name": "CAST(C_NATIONKEY AS LONG)", "alias": "C_NATIONKEY" },
         |      { "name": "C_PHONE" }
         |    ]
         |  },
         |  "_comment": "The `target` field is a general field that manages the target database. Is basically where we write the data that has been read in the field `source`",
         |  "target": {
         |    "_comment": "The `format` field manages the connector datasource",
         |    "format": "org.neo4j.spark.DataSource",
         |    "_comment": "The `mode` is the save mode of the writing connector",
         |    "mode": "Overwrite",
         |    "_comment": "The `options` field manages the connector datasource options, which are specific for each datasource",
         |    "options": {
         |      "labels": ":Person:Customer",
         |      "url": "${neo4jContainer.getBoltUrl}",
         |      "node.keys": "C_CUSTKEY"
         |    }
         |  }
         |}
         |""".stripMargin

    runJob(jsonConfig)
    val driver = GraphDatabase.driver(neo4jContainer.getBoltUrl)
    val neo4jSession = driver.session()
    try {
      val count = neo4jSession.run(
        """
          |MATCH (p:Person:Customer)
          |RETURN count(p) AS count
          |""".stripMargin)
        .single()
        .get(0)
        .asLong()
      Assert.assertEquals(10L, count)
    } finally {
      neo4jSession.close()
      driver.close()
    }
  }

  @Test
  def shouldImportNeo4jIntoSnowflake(): Unit = {
    val snowflakeschema = Properties.envOrNone("SNOWFLAKE_SCHEMA")
    Assume.assumeFalse(snowflakeschema.isEmpty)
    val snowflakeuser = Properties.envOrNone("SNOWFLAKE_USER")
    Assume.assumeFalse(snowflakeuser.isEmpty)
    val snowflakepassword = Properties.envOrNone("SNOWFLAKE_PASSWORD")
    Assume.assumeFalse(snowflakepassword.isEmpty)
    val snowflakedatabase = Properties.envOrNone("SNOWFLAKE_DATABASE")
    Assume.assumeFalse(snowflakedatabase.isEmpty)
    val snowflakeurl = Properties.envOrNone("SNOWFLAKE_URL")
    Assume.assumeFalse(snowflakeurl.isEmpty)
    val snowflaketable = Properties.envOrNone("SNOWFLAKE_TABLE")
    Assume.assumeFalse(snowflaketable.isEmpty)
    val numPersons = 10
    createPersons(numPersons)
    val jsonConfig =
      s"""
         |{
         |  "name": "Create Person from Neo4j to Snowflake",
         |  "master": "local",
         |  "_comment": "The `source` field is a general field that manages the source database. Is basically where we read the data",
         |  "source": {
         |    "_comment": "The `format` field manages the connector datasource",
         |    "format": "org.neo4j.spark.DataSource",
         |    "options": {
         |      "labels": ":Person:Customer",
         |      "url": "${neo4jContainer.getBoltUrl}"
         |    },
         |    "columns": [
         |      { "name": "ID" },
         |      { "name": "NAME" },
         |      { "name": "SURNAME" },
         |      { "name": "AGE" }
         |    ]
         |  },
         |  "_comment": "The `target` field is a general field that manages the target database. Is basically where we write the data that has been read in the field `source`",
         |  "target": {
         |    "_comment": "The `format` field manages the connector datasource",
         |    "format" : "snowflake",
         |    "_comment": "The `mode` is the save mode of the writing connector",
         |    "mode": "Append",
         |    "_comment": "The `options` field manages the connector datasource options, which are specific for each datasource",
         |    "options" : {
         |      "sfSchema" : "${snowflakeschema.get}",
         |      "sfPassword" : "${snowflakepassword.get}",
         |      "sfUser" : "${snowflakeuser.get}",
         |      "dbtable" : "${snowflaketable.get}",
         |      "sfDatabase" : "${snowflakedatabase.get}",
         |      "sfURL" : "${snowflakeurl.get}"
         |    }
         |  }
         |}
         |""".stripMargin

    runJob(jsonConfig)

    val count = SparkSession.builder()
      .master("local[*]")
      .getOrCreate()
      .read
      .format("snowflake")
      .option("sfSchema", snowflakeschema.get)
      .option("sfPassword", snowflakepassword.get)
      .option("sfUser", snowflakeuser.get)
      .option("dbtable", snowflaketable.get)
      .option("sfDatabase", snowflakedatabase.get)
      .option("sfURL", snowflakeurl.get)
      .load()
      .count()
    Assert.assertEquals(numPersons.toLong, count)
  }

  @Test
  def shouldImportBigQueryIntoNeo4j(): Unit = {
    val googleprojectid = Properties.envOrNone("GOOGLE_PROJECT_ID")
    Assume.assumeFalse(googleprojectid.isEmpty)
    val googlecredentialsjson = Properties.envOrNone("GOOGLE_CREDENTIALS_JSON")
    Assume.assumeFalse(googlecredentialsjson.isEmpty)
    val jsonConfig =
      s"""
         |{
         |  "name": "Create ingest BigQuery's Stackoverflow data into Neo4j",
         |  "master": "local",
         |  "_comment": "The `source` field is a general field that manages the source database. Is basically where we read the data",
         |  "source": {
         |    "_comment": "The `format` field manages the connector datasource",
         |    "format" : "bigquery",
         |    "_comment": "The `options` field manages the connector datasource options, which are specific for each datasource",
         |    "options": {
         |      "table": "bigquery-public-data.stackoverflow.posts_questions",
         |      "parentProject": "${googleprojectid.get}",
         |      "credentialsFile": "${googlecredentialsjson.get}"
         |    },
         |    "_comment": "The `where` field manages filters on the datasource (not mandatory)",
         |    "where": "id <= 10",
         |    "_comments": "The `columns` field manages the projection of Dataframe columns",
         |    "columns": [
         |      { "name": "ID" },
         |      { "name": "TITLE" },
         |      { "name": "BODY" }
         |    ]
         |  },
         |  "_comment": "The `target` field is a general field that manages the target database. Is basically where we write the data that has been read in the field `source`",
         |  "target": {
         |    "_comment": "The `format` field manages the connector datasource",
         |    "format": "org.neo4j.spark.DataSource",
         |    "_comment": "The `mode` is the save mode of the writing connector",
         |    "mode": "Overwrite",
         |    "_comment": "The `options` field manages the connector datasource options, which are specific for each datasource",
         |    "options": {
         |      "labels": ":Answer",
         |      "url": "${neo4jContainer.getBoltUrl}",
         |      "node.keys": "ID"
         |    }
         |  }
         |}
         |""".stripMargin

    runJob(jsonConfig)
    val driver = GraphDatabase.driver(neo4jContainer.getBoltUrl)
    val neo4jSession = driver.session()
    try {
      val count = neo4jSession.run(
        """
          |MATCH (p:Answer)
          |RETURN count(p) AS count
          |""".stripMargin)
        .single()
        .get(0)
        .asLong()
      Assert.assertEquals(3L, count)
    } finally {
      neo4jSession.close()
      driver.close()
    }
  }

  @Test
  def shouldImportNeo4jIntoBigQuery(): Unit = {
    val googleprojectid = Properties.envOrNone("GOOGLE_PROJECT_ID")
    Assume.assumeFalse(googleprojectid.isEmpty)
    val googlecredentialsjson = Properties.envOrNone("GOOGLE_CREDENTIALS_JSON")
    Assume.assumeFalse(googlecredentialsjson.isEmpty)
    val googlebigquerytable = Properties.envOrNone("GOOGLE_BIGQUERY_TABLE")
    Assume.assumeFalse(googlebigquerytable.isEmpty)
    val numPersons = 10
    createPersons(numPersons)
    val jsonConfig =
      s"""
         |{
         |  "name": "Create ingest BigQuery's Stackoverflow data into Neo4j",
         |  "master": "local",
         |  "_comment": "The `source` field is a general field that manages the source database. Is basically where we read the data",
         |  "source": {
         |    "_comment": "The `format` field manages the connector datasource",
         |    "format": "org.neo4j.spark.DataSource",
         |    "_comment": "The `options` field manages the connector datasource options, which are specific for each datasource",
         |    "options": {
         |      "labels": ":Person",
         |      "url": "${neo4jContainer.getBoltUrl}"
         |    },
         |    "columns": [
         |      { "name": "ID" },
         |      { "name": "NAME" },
         |      { "name": "SURNAME" },
         |      { "name": "AGE" }
         |    ]
         |  },
         |  "_comment": "The `target` field is a general field that manages the target database. Is basically where we write the data that has been read in the field `source`",
         |  "target": {
         |    "_comment": "The `format` field manages the connector datasource",
         |    "format" : "bigquery",
         |    "_comment": "The `mode` is the save mode of the writing connector",
         |    "mode": "Overwrite",
         |    "_comment": "The `options` field manages the connector datasource options, which are specific for each datasource",
         |    "options" : {
         |      "table": "${googlebigquerytable.get}",
         |      "parentProject": "${googleprojectid.get}",
         |      "credentialsFile": "${googlecredentialsjson.get}"
         |    }
         |  }
         |}
         |""".stripMargin

    runJob(jsonConfig)

    val count = SparkSession.builder()
      .master("local[*]")
      .getOrCreate()
      .read
      .format("bigquery")
      .option("table", googlebigquerytable.get)
      .option("parentProject", googleprojectid.get)
      .option("credentialsFile", googlecredentialsjson.get)
      .load()
      .count()
    Assert.assertEquals(numPersons.toLong, count)
  }

  @Test
  def shouldImportRedShiftIntoNeo4j(): Unit = {
    val awsredshifturl = Properties.envOrNone("AWS_REDSHIFT_URL")
    Assume.assumeFalse(awsredshifturl.isEmpty)
    val awsredshifttable = Properties.envOrNone("AWS_REDSHIFT_TABLE")
    Assume.assumeFalse(awsredshifttable.isEmpty)
    val awsiamrole = Properties.envOrNone("AWS_IAM_ROLE")
    Assume.assumeFalse(awsiamrole.isEmpty)
    val awss3tmpdir = Properties.envOrNone("AWS_S3_TMPDIR")
    Assume.assumeFalse(awss3tmpdir.isEmpty)
    val awss3accessid = Properties.envOrNone("AWS_ACCESS_KEY")
    Assume.assumeFalse(awss3accessid.isEmpty)
    val awss3accessky = Properties.envOrNone("AWS_SECRET_ACCESS_KEY")
    Assume.assumeFalse(awss3accessky.isEmpty)
    val jsonConfig =
      s"""
         |{
         |  "name": "Create ingest RedShift data into Neo4j",
         |  "master": "local",
         |  "hadoopConfiguration": {
         |    "fs.s3a.access.key": "${awss3accessid.get}",
         |    "fs.s3a.secret.key": "${awss3accessky.get}"
         |  },
         |  "_comment": "The `source` field is a general field that manages the source database. Is basically where we read the data",
         |  "source": {
         |    "_comment": "The `format` field manages the connector datasource",
         |    "format" : "io.github.spark_redshift_community.spark.redshift",
         |    "_comment": "The `options` field manages the connector datasource options, which are specific for each datasource",
         |    "options": {
         |      "url": "${awsredshifturl.get}",
         |      "dbtable": "${awsredshifttable.get}",
         |      "tempdir": "${awss3tmpdir.get}",
         |      "forward_spark_s3_credentials": "true"
         |    },
         |    "_comment": "The `where` field manages filters on the datasource (not mandatory)",
         |    "where": "userid <= 10"
         |  },
         |  "_comment": "The `target` field is a general field that manages the target database. Is basically where we write the data that has been read in the field `source`",
         |  "target": {
         |    "_comment": "The `format` field manages the connector datasource",
         |    "format": "org.neo4j.spark.DataSource",
         |    "_comment": "The `mode` is the save mode of the writing connector",
         |    "mode": "Overwrite",
         |    "_comment": "The `options` field manages the connector datasource options, which are specific for each datasource",
         |    "options": {
         |      "labels": ":Person",
         |      "url": "${neo4jContainer.getBoltUrl}",
         |      "node.keys": "userid"
         |    }
         |  }
         |}
         |""".stripMargin

    runJob(jsonConfig)
    val driver = GraphDatabase.driver(neo4jContainer.getBoltUrl)
    val neo4jSession = driver.session()
    try {
      val count = neo4jSession.run(
        """
          |MATCH (p:Person)
          |RETURN count(p) AS count
          |""".stripMargin)
        .single()
        .get(0)
        .asLong()
      Assert.assertEquals(10L, count)
    } finally {
      neo4jSession.close()
      driver.close()
    }
  }

  @Test
  def shouldImportNeo4jIntoRedShift(): Unit = {
    val awsredshifturl = Properties.envOrNone("AWS_REDSHIFT_URL")
    Assume.assumeFalse(awsredshifturl.isEmpty)
    val awsredshifttable = Properties.envOrNone("AWS_REDSHIFT_TABLE")
    Assume.assumeFalse(awsredshifttable.isEmpty)
    val awsiamrole = Properties.envOrNone("AWS_IAM_ROLE")
    Assume.assumeFalse(awsiamrole.isEmpty)
    val awss3tmpdir = Properties.envOrNone("AWS_S3_TMPDIR")
    Assume.assumeFalse(awss3tmpdir.isEmpty)
    val awss3accessid = Properties.envOrNone("AWS_ACCESS_KEY")
    Assume.assumeFalse(awss3accessid.isEmpty)
    val awss3accessky = Properties.envOrNone("AWS_SECRET_ACCESS_KEY")
    Assume.assumeFalse(awss3accessky.isEmpty)
    val numPersons = 10
    createPersons(numPersons)
    val jsonConfig =
      s"""
         |{
         |  "name": "Create ingest RedShift data into Neo4j",
         |  "master": "local",
         |  "hadoopConfiguration": {
         |    "fs.s3a.access.key": "${awss3accessid.get}",
         |    "fs.s3a.secret.key": "${awss3accessky.get}"
         |  },
         |  "_comment": "The `source` field is a general field that manages the source database. Is basically where we read the data",
         |  "source": {
         |    "_comment": "The `format` field manages the connector datasource",
         |    "format": "org.neo4j.spark.DataSource",
         |    "_comment": "The `options` field manages the connector datasource options, which are specific for each datasource",
         |    "options": {
         |      "labels": ":Person",
         |      "url": "${neo4jContainer.getBoltUrl}"
         |    },
         |    "columns": [
         |      { "name": "ID" },
         |      { "name": "NAME" },
         |      { "name": "SURNAME" },
         |      { "name": "AGE" }
         |    ]
         |  },
         |  "_comment": "The `target` field is a general field that manages the target database. Is basically where we write the data that has been read in the field `source`",
         |  "target": {
         |    "_comment": "The `format` field manages the connector datasource",
         |    "format" : "io.github.spark_redshift_community.spark.redshift",
         |    "_comment": "The `options` field manages the connector datasource options, which are specific for each datasource",
         |    "options": {
         |      "url": "${awsredshifturl.get}",
         |      "dbtable": "${awsredshifttable.get}",
         |      "tempdir": "${awss3tmpdir.get}",
         |      "tempformat": "CSV",
         |      "forward_spark_s3_credentials": "true"
         |    }
         |  }
         |}
         |""".stripMargin

    runJob(jsonConfig)

    val session = SparkSession.builder()
      .master("local[*]")
      .getOrCreate()
    session.sparkContext.hadoopConfiguration.set("fs.s3a.access.key", awss3accessid.get)
    session.sparkContext.hadoopConfiguration.set("fs.s3a.secret.key", awss3accessky.get)
    val count = session
      .read
      .format("io.github.spark_redshift_community.spark.redshift")
      .option("url", awsredshifturl.get)
      .option("dbtable", awsredshifttable.get)
      .option("tempdir", awss3tmpdir.get)
      .option("forward_spark_s3_credentials", "true")
      .load()
      .count()
    Assert.assertEquals(numPersons.toLong, count)
  }

  /**
   * Synapse Connector is available only in Databricks Cloud
   * if you want to Connect to it in non-Databricks environment
   * you can user the `jdbc` Datasource with a job like this:
   *
   * Read:
   * spark.read
   *  .format("jdbc")
   *  .option("url", "jdbc:sqlserver://synapsesparkneo4j.sql.azuresynapse.net:1433;database=<database_name>;user=<user>;password=<password>;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.sql.azuresynapse.net;loginTimeout=30;")
   *  .option("dbtable", "dbo.Date")
   *  .load()
   *
   * Write:
   * df.write
   *  .format("jdbc")
   *  .option("url", "jdbc:sqlserver://synapsesparkneo4j.sql.azuresynapse.net:1433;database=<database_name>;user=<user>;password=<password>;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.sql.azuresynapse.net;loginTimeout=30;")
   *  .option("dbtable", "dbo.Date")
   *  .save()
   */

  private def runJob(jsonConfig: String) = {
    val jobConfig = JobConfig.from(jsonConfig)
    new Neo4jDWHConnector(jobConfig).run()
  }
}
