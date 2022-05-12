import sys

if sys.version > '3':
    basestring = str

from pyspark.sql import SparkSession

from neo4j_dwh_connector._dto import JobConfig
from neo4j_dwh_connector._utils import _to_java_value


class Neo4jDWHConnector:

    def __init__(self, session: SparkSession, jobConfig: JobConfig):
        java_map = _to_java_value(jobConfig, session.sparkContext)
        self._jvm_connector = session.sparkContext._jvm.org.neo4j.dwh.connector.Neo4jDWHConnector(
            session._jsparkSession, java_map)

    def run(self, closeSession=False):
        self._jvm_connector.run(closeSession)
