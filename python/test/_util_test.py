import unittest
from py4j.java_collections import JavaMap, JavaList
from pyspark.sql import SparkSession

from neo4j_dwh_connector._dto import *
from neo4j_dwh_connector._utils import _to_java_value


class UtilTest(unittest.TestCase):
    spark = None

    def setUp(self):
        self.spark = (SparkSession.builder
                      .appName("Neo4jConnectorTests")
                      .master('local[*]')
                      # .config('spark.jars.packages', 'org.neo4j:neo4j-connector-apache-spark_2.12:4.1.2_for_spark_3')
                      .config("spark.driver.host", "127.0.0.1")
                      .getOrCreate())

    def test__to_java_value(self):
        source = Source(
            format="snowflake",  # the source database (mandatory)
            # the configuration options it will change for every source database (mandatory)
            options={
                "sfSchema": "TPCH_SF1",
                "sfPassword": "****",
                "sfUser": "****",
                "dbtable": "CUSTOMER",
                "sfDatabase": "SNOWFLAKE_SAMPLE_DATA",
                "sfURL": "https://****.eu-central-1.snowflakecomputing.com"
            },
            # a list of selected projected columns, it can be usefull in order to eventually cast data,
            # apply Spark's UDFs and minimize the data movement from the source database (optional)
            columns=[
                Column(name="CAST(C_ACCTBAL AS DOUBLE)", alias="C_ACCTBAL"),
                Column(name="C_ADDRESS"),
                Column(name="C_COMMENT"),
                Column(name="CAST(C_CUSTKEY AS LONG)", alias="C_CUSTKEY"),
                Column(name="C_MKTSEGMENT"),
                Column(name="C_NAME"),
                Column(name="CAST(C_NATIONKEY AS LONG)", alias="C_NATIONKEY"),
                Column(name="C_PHONE")
            ],
            where="",  # a filter for the source dataset (optional)
            printSchema=True,  # if you want to print the schema, useful for debug purposes (optional)
            show=5,  # if you want show the source database, useful for debug purposes (optional)
            limit=10,  # the amount of rows that you want to have from the source dataset (optional)
            # a dataframe partition configuration (optional)
            partition=Partition(
                number=-1,  # the number of partions mandatory if you want to define partitions
                by=""  # the field to partition (optional)
            )
        )
        # the target database configuration
        target = Target(
            format="org.neo4j.spark.DataSource",  # the target database (mandatory)
            # the configuration options it will change for every source database (mandatory)
            options={
                "labels": ":PersonNew1:CustomerNew1",
                "url": "neo4j+s://****.databases.neo4j.io",
                "authentication.basic.username": "neo4j",
                "authentication.basic.password": "****",
                "node.keys": "C_CUSTKEY"
            },
            mode="Overwrite"
        )

        config = JobConfig(
            name="The name of the Spark Job",
            conf={},  # a <String,String> configuration map, every k/v binding will be insert as Spark Configuration
            hadoopConfiguration={},
            # a <String,String> configuration map, every k/v binding will be insert as Hadoop Configuration
            source=source,
            target=target
        )

        converted = _to_java_value(config, self.spark)
        assert type(converted) is JavaMap
        assert type(converted["source"]) is JavaMap
        assert type(converted["target"]) is JavaMap
        assert type(converted["source"]["columns"]) is JavaList
        for id, value in enumerate(converted["source"]["columns"]):
            assert type(converted["source"]["columns"][id]) is JavaMap


if __name__ == "__main__":
    unittest.main()
