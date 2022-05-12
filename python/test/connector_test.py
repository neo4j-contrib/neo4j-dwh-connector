import sys
import unittest
from unittest import SkipTest

from pyspark.sql import SparkSession
from testcontainers.neo4j import Neo4jContainer
from tzlocal import get_localzone

from neo4j_dwh_connector import *

import pathlib

connector_version = "1.0-SNAPSHOT"
neo4j_version = "4.4-enterprise"
current_time_zone = get_localzone().zone


def parse_arguments(length=4):
    print(sys.argv)
    global connector_version
    global neo4j_version
    global current_time_zone
    if len(sys.argv) >= length - 1:
        if length - 1 in sys.argv:
            current_time_zone = sys.argv.pop()  # str(sys.argv[++start_index])
        neo4j_version = sys.argv.pop()  # str(sys.argv[++start_index])
        connector_version = sys.argv.pop()  # str(sys.argv[start_index])
    print("Running tests for Connector %s, Neo4j %s, TimeZone %s"
          % (connector_version, neo4j_version, current_time_zone))


class ConnectorTest(unittest.TestCase):
    neo4j_container = None
    spark = None

    @classmethod
    def setUpClass(cls):
        jar_path = "target/neo4j-dwh-connector-{version}-jar-with-dependencies.jar".format(
            version=connector_version)
        jar_file = (pathlib.Path(__file__)
                    .absolute()
                    .parent
                    .parent
                    .parent
                    .joinpath(jar_path))
        if not jar_file.exists():
            path_format_error = 'Connector JAR not found under $PROJECT_HOME/{path}'.format(path=jar_path)
            print(path_format_error)
            raise SkipTest(path_format_error)
        cls.neo4j_container = (Neo4jContainer('neo4j:' + neo4j_version)
                               .with_env("NEO4J_db_temporal_timezone", current_time_zone)
                               .with_env("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes"))
        cls.neo4j_container.start()
        cls.spark = (SparkSession.builder
                     .appName("Neo4jConnectorTests")
                     .master('local[*]')
                     .config('spark.jars.packages', 'org.neo4j:neo4j-connector-apache-spark_2.12:4.1.2_for_spark_3')
                     .config("spark.jars", str(jar_file))
                     .config("spark.driver.host", "127.0.0.1")
                     .getOrCreate())

    @classmethod
    def tearDownClass(cls):
        cls.neo4j_container.stop()
        cls.spark.stop()

    def test_ingest_csv(self):
        csv_path = (pathlib.Path(__file__)
                    .absolute()
                    .parent
                    .parent
                    .parent
                    .joinpath("src/test/resources/persons.csv"))
        assert csv_path.exists()
        source = Source(
            format="csv",  # the source database (mandatory)
            # the configuration options it will change for every source database (mandatory)
            options={
                "header": "true",
                "path": str(csv_path)
            },
            # a list of selected projected columns, it can be useful in order to eventually cast data,
            # apply Spark's UDFs and minimize the data movement from the source database (optional)
            columns=[
                Column(name="person_id", alias="id"),
                Column(name="person_name", alias="name")
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
                "labels": ":Person:Customer",
                "url": self.neo4j_container.get_connection_url(),
                "authentication.basic.username": Neo4jContainer.NEO4J_USER,
                "authentication.basic.password": Neo4jContainer.NEO4J_ADMIN_PASSWORD,
                "node.keys": "id"
            },
            mode="Overwrite"
        )

        config = JobConfig(
            name="Create Persons from CSV to Neo4j",
            conf={},  # a <String,String> configuration map, every k/v binding will be insert as Spark Configuration
            hadoopConfiguration={},
            # a <String,String> configuration map, every k/v binding will be insert as Hadoop Configuration
            source=source,
            target=target,
            master="local"
        )
        connector = Neo4jDWHConnector(self.spark, config)

        # this will ingest the data from source to target database
        connector.run()

        with self.neo4j_container.get_driver() as neo4j_driver:
            with neo4j_driver.session() as neo4j_session:
                result = neo4j_session.run("MATCH (n:Person:Customer) RETURN count(n) AS count").peek()
                assert result["count"] == 3


if __name__ == "__main__":
    parse_arguments()
    unittest.main()
