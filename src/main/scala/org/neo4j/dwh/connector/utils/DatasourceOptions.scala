package org.neo4j.dwh.connector.utils

import org.neo4j.dwh.connector.domain.{Column, Partition, Source, Target}

object DatasourceOptions extends Enumeration {
  case class DatasourceOptionsValue(options: Map[String, Any], deps: Array[String],
                                    conf: Map[String, String] = Map.empty,
                                    hadoopConf: Map[String, String] = Map.empty) extends super.Val {
    def toSource(): Source = JSONUtils.mapper.convertValue(options, classOf[Source])
    def toTarget(): Target = JSONUtils.mapper.convertValue(options, classOf[Target])
  }

  private val cols = Array(Column("""<Dataframe column name to project>.
                                  |N.b. `columns` field will be ignored in case
                                  |you're using it in the `target` field
                                  |""".stripMargin, "Alias to column, not mandatory"))

  private val partition = Partition(-1)

  def withNameIgnoreCase(name: String): DatasourceOptionsValue = this.values
    .filter(_.toString.equalsIgnoreCase(name))
    .headOption
    .getOrElse(() => throw new NoSuchElementException(s"No value for $name"))
    .asInstanceOf[DatasourceOptionsValue]

  val Snowflake = DatasourceOptionsValue(Map(
    "format" -> "snowflake",
    "columns" -> cols,
    "where" -> "<Spark SQL filter to the Dataframe.> It will be ignored in case you're using it in the `target` field.",
    "mode" ->
      """<Spark Save Mode>
        |N.b. It'll be ignored if you're reading data from Snowflake.
        |Please check supported save modes here: https://docs.snowflake.com/en/user-guide/spark-connector-use.html#moving-data-from-spark-to-snowflake
        |""".stripMargin,
    "options" -> Map(
      "_comment" ->
        """<This field is a comment you can simple ignore it>
          |You can find the full list of Snowflake configuration properties here:
          |https://docs.snowflake.com/en/user-guide/spark-connector-use.html#setting-configuration-options-for-the-connector
          |""".stripMargin,
      "sfURL" -> "<account_identifier>.snowflakecomputing.com",
      "sfUser" -> "<user_name>",
      "sfPassword" -> "<password>",
      "sfDatabase" -> "<database>",
      "sfSchema" -> "<schema>",
      "sfWarehouse" -> "<warehouse>",
      "dbtable" -> "<snowflake-table>"),
    "partition" -> partition
  ), Array("net.snowflake:spark-snowflake_<scala_version>:<version>", "net.snowflake:snowflake-jdbc:<version>"))
  val Neo4j = DatasourceOptionsValue(Map(
    "format" -> "org.neo4j.spark.DataSource",
    "columns" -> cols,
    "where" -> ">Spark SQL filter to the Dataframe> It will be ignored in case you're using it in the `target` field.",
    "mode" ->
      """<Spark Save Mode>
        |N.b. It'll be ignored if you're reading data from Neo4j.
        |Please check supported save modes here: https://neo4j.com/docs/spark/current/writing/#save-mode
        |""".stripMargin,
    "options" -> Map(
      "_comment" ->
        """<This field is a comment you can simple ignore it>
          |You can find the full list of Neo4j configuration properties here:
          |https://neo4j.com/docs/spark/current/
          |""".stripMargin,
      "labels" ->
        """<List of node labels separated by `:` The first label will be the primary label>.
          |In case of writing into Neo4j please see https://neo4j.com/docs/spark/current/writing/#write-node
          |In case of reading from Neo4j please see https://neo4j.com/docs/spark/current/reading/#read-node
          |""".stripMargin,
      "relationship" ->
        """<The relationship type>
          |N.b. this field requires extra configuration please see
          | - In case of writing: https://neo4j.com/docs/spark/current/writing/#write-rel
          | - In case of reading: https://neo4j.com/docs/spark/current/reading/#read-rel
          |""".stripMargin,
      "query" ->
        """<The neo4j cypher query>
          |In case of writing into Neo4j please see https://neo4j.com/docs/spark/current/writing/#write-query
          |In case of reading from Neo4j please see https://neo4j.com/docs/spark/current/reading/#read-query
          |""".stripMargin,
      "url" -> "<neo4j_url>",
      "authentication.type" -> "<auth type> Please see: https://neo4j.com/docs/spark/current/configuration/",
      "authentication.basic.username" -> "<neo4j_user_name>",
      "authentication.basic.password" -> "<neo4j_password>"),
    "partition" -> partition
  ), Array("org.neo4j:neo4j-connector-apache-spark_<scala_version>:<version>"))
  val BigQuery = DatasourceOptionsValue(Map(
    "format" -> "bigquery",
    "columns" -> cols,
    "where" -> ">Spark SQL filter to the Dataframe> It will be ignored in case you're using it in the `target` field.",
    "mode" -> "<Spark Save Mode>",
    "options" -> Map(
      "_comment" ->
        """<This field is a comment you can simple ignore it>
          |You can find the full list of BigQuery configuration properties here:
          |https://github.com/GoogleCloudDataproc/spark-bigquery-connector#properties
          |""".stripMargin,
      "path" -> "The BigQuery table in the format [[project:]dataset.]table",
      "credentials" -> "<SERVICE_ACCOUNT_JSON_IN_BASE64>",
      "dataset" -> "The dataset containing the table. This option should be used with standard table and views, but not when loading query results."
    ),
    "partition" -> partition
  ), Array("com.google.cloud.spark:spark-bigquery-with-dependencies_<scala_version>:<version>"))
  val RedShift_Community = DatasourceOptionsValue(Map(
      "format" -> "io.github.spark_redshift_community.spark.redshift",
      "columns" -> cols,
      "where" -> ">Spark SQL filter to the Dataframe> It will be ignored in case you're using it in the `target` field.",
      "mode" -> "<Spark Save Mode>",
      "options" -> Map(
        "_comment" ->
          """<This field is a comment you can simple ignore it>
            |You can find the full list of RedShift configuration properties here:
            |https://github.com/spark-redshift-community/spark-redshift#parameters
            |""".stripMargin,
        "url" ->
          """A JDBC URL, of the format, jdbc:subprotocol://host:port/database?user=username&password=password
            |subprotocol can be postgresql or redshift, depending on which JDBC driver you have loaded. Note however that one Redshift-compatible driver must be on the classpath and match this URL.
            |host and port should point to the Redshift master node, so security groups and/or VPC will need to be configured to allow access from your driver application.
            |database identifies a Redshift database name
            |user and password are credentials to access the database, which must be embedded in this URL for JDBC, and your user account should have necessary privileges for the table being referenced.
            |""".stripMargin,
        "query" -> "The query to read from in Redshift (unless `dbtable` is specified)",
        "dbtable" -> "The table to create or read from in Redshift. This parameter is required when saving data back to Redshift.",
        "tempdir" -> "A writeable location in Amazon S3, to be used for unloaded data when reading and Avro data to be loaded into Redshift when writing. If you're using Redshift data source for Spark as part of a regular ETL pipeline, it can be useful to set a Lifecycle Policy on a bucket and use that as a temp location for this data."
      ),
      "partition" -> partition
    ),
    Array("com.amazonaws:aws-java-sdk:<version>", "com.amazon.redshift:redshift-jdbc42:<version>", "org.apache.spark:spark-avro_<scala_version>:<version>", "io.github.spark-redshift-community:spark-redshift_<scala_version>:<version>"),
    Map.empty,
    Map("fs.s3<n/a depends by the s3 that you want to use>.awsAccessKeyId" -> "YOUR_KEY_ID", "fs.s3<n/a depends by the s3 that you want to use>.awsSecretAccessKey" -> "YOUR_SECRET_ACCESS_KEY")
  )
  val RedShift_Databricks = DatasourceOptionsValue(Map(
    "format" -> "com.databricks.spark.redshift",
    "columns" -> cols,
    "where" -> ">Spark SQL filter to the Dataframe> It will be ignored in case you're using it in the `target` field.",
    "mode" -> "<Spark Save Mode>",
    "options" -> Map(
      "_comment" ->
        """<This field is a comment you can simple ignore it>
          |You can find the full list of RedShift configuration properties here:
          |https://github.com/spark-redshift-community/spark-redshift#parameters
          |""".stripMargin,
      "url" ->
        """A JDBC URL, of the format, jdbc:subprotocol://host:port/database?user=username&password=password
          |subprotocol can be postgresql or redshift, depending on which JDBC driver you have loaded. Note however that one Redshift-compatible driver must be on the classpath and match this URL.
          |host and port should point to the Redshift master node, so security groups and/or VPC will need to be configured to allow access from your driver application.
          |database identifies a Redshift database name
          |user and password are credentials to access the database, which must be embedded in this URL for JDBC, and your user account should have necessary privileges for the table being referenced.
          |""".stripMargin,
      "query" -> "The query to read from in Redshift (unless `dbtable` is specified)",
      "dbtable" -> "The table to create or read from in Redshift. This parameter is required when saving data back to Redshift.",
      "tempdir" -> "A writeable location in Amazon S3, to be used for unloaded data when reading and Avro data to be loaded into Redshift when writing. If you're using Redshift data source for Spark as part of a regular ETL pipeline, it can be useful to set a Lifecycle Policy on a bucket and use that as a temp location for this data."
    ),
    "partition" -> partition
  ),
    Array("com.amazonaws:aws-java-sdk:<version>", "com.amazon.redshift:redshift-jdbc42:<version>", "org.apache.spark:spark-avro_<scala_version>:<version>"),
    Map.empty,
    Map("fs.s3<n/a depends by the s3 that you want to use>.awsAccessKeyId" -> "YOUR_KEY_ID", "fs.s3<n/a depends by the s3 that you want to use>.awsSecretAccessKey" -> "YOUR_SECRET_ACCESS_KEY")
  )
  val RedShift_JDBC = DatasourceOptionsValue(Map(
    "format" -> "jdbc",
    "columns" -> cols,
    "where" -> ">Spark SQL filter to the Dataframe> It will be ignored in case you're using it in the `target` field.",
    "mode" -> "<Spark Save Mode>",
    "options" -> Map(
      "_comment" ->
        """<This field is a comment you can simple ignore it>
          |You can connect to RedShift in a non Databricks env also via JDBC.
          |Please refer to this documentation page:
          |https://spark.apache.org/docs/latest/sql-data-sources-jdbc.html
          |""".stripMargin,
      "url" ->
        """A JDBC URL, of the format, jdbc:subprotocol://host:port/database?user=username&password=password
          |subprotocol can be postgresql or redshift, depending on which JDBC driver you have loaded. Note however that one Redshift-compatible driver must be on the classpath and match this URL.
          |host and port should point to the Redshift master node, so security groups and/or VPC will need to be configured to allow access from your driver application.
          |database identifies a Redshift database name
          |user and password are credentials to access the database, which must be embedded in this URL for JDBC, and your user account should have necessary privileges for the table being referenced.
          |""".stripMargin,
      "query" -> "The query to read from in Redshift (unless `dbtable` is specified)",
      "dbtable" -> "The table to create or read from in Redshift. This parameter is required when saving data back to Redshift."
    ),
    "partition" -> partition
  ), Array.empty)
  val Synapse_Databricks = DatasourceOptionsValue(Map(
    "format" -> "com.databricks.spark.sqldw",
    "columns" -> cols,
    "where" -> ">Spark SQL filter to the Dataframe> It will be ignored in case you're using it in the `target` field.",
    "mode" -> "<Spark Save Mode>",
    "options" -> Map(
      "_comment" ->
        """<This field is a comment you can simple ignore it>
          |You can find the full list of Azure Synapse Analytics configuration properties here:
          |https://docs.microsoft.com/en-us/azure/databricks/data/data-sources/azure/synapse-analytics#parameters
          |""".stripMargin,
      "url" -> "A JDBC URL with sqlserver set as the subprotocol. It is recommended to use the connection string provided by Azure portal. Setting\nencrypt=true is strongly recommended, because it enables SSL encryption of the JDBC connection. If user and password are set separately, you do not need to include them in the URL.",
      "tempDir" -> "A wasbs URI. We recommend you use a dedicated Blob storage container for the Azure Synapse.",
      "forwardSparkAzureStorageCredentials" ->
        """If true, the library automatically discovers the credentials that Spark is using to connect to the Blob storage container and forwards those credentials to Azure Synapse over JDBC. These credentials are sent as part of the JDBC query. Therefore it is strongly recommended that you enable SSL encryption of the JDBC connection when you use this option.
          |The current version of Azure Synapse connector requires (exactly) one of forwardSparkAzureStorageCredentials, enableServicePrincipalAuth, or useAzureMSI to be explicitly set to true.
          |The previously supported forward_spark_azure_storage_credentials variant is deprecated and will be ignored in future releases. Use the “camel case” name instead.
          |""".stripMargin,
      "query" -> "The query to read from in Synapse (unless `dbtable` is specified)",
      "dbTable" ->
        """The table to create or read from in Azure Synapse. This parameter is required when saving data back to Azure Synapse.
          |You can also use {SCHEMA NAME}.{TABLE NAME} to access a table in a given schema. If schema name is not provided, the default schema associated with the JDBC user is used.
          |The previously supported dbtable variant is deprecated and will be ignored in future releases. Use the “camel case” name instead.""".stripMargin
    ),
    "partition" -> partition
  ), Array("com.microsoft.azure:spark-mssql-connector_<scala_version>:<version>"), Map(
    "fs.azure.account.key.<your-storage-account-name>.dfs.core.windows.net" -> "<your-storage-account-access-key>"
  ))
  val Synapse_JDBC = DatasourceOptionsValue(Map(
    "format" -> "jdbc",
    "columns" -> cols,
    "where" -> ">Spark SQL filter to the Dataframe> It will be ignored in case you're using it in the `target` field.",
    "mode" -> "<Spark Save Mode>",
    "options" -> Map(
      "_comment" ->
        """<This field is a comment you can simple ignore it>
          |You can connect to Synapse in a non Databricks env only via JDBC driver.
          |Please refer to this documentation page:
          |https://spark.apache.org/docs/latest/sql-data-sources-jdbc.html
          |""".stripMargin,
      "url" -> "A JDBC URL with sqlserver set as the subprotocol. It is recommended to use the connection string provided by Azure portal. Setting\nencrypt=true is strongly recommended, because it enables SSL encryption of the JDBC connection. If user and password are set separately, you do not need to include them in the URL.",
      "query" -> "The query to read from in Synapse (unless `dbtable` is specified)",
      "dbtable" ->
        """The table to create or read from in Azure Synapse. This parameter is required when saving data back to Azure Synapse.
          |You can also use {SCHEMA NAME}.{TABLE NAME} to access a table in a given schema. If schema name is not provided, the default schema associated with the JDBC user is used.""".stripMargin
    ),
    "partition" -> partition
  ), Array.empty)
}
