#!/bin/bash

if [[ $# -lt 3 ]] ; then
    echo "Usage ./maven-release.sh <DEPLOY_OR_INSTALL> <SCALA-VERSION> <SPARK-VERSION> [<ALT_DEPLOYMENT_REPOSITORY>]"
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | grep -i version)

if [[ ! $JAVA_VER =~ 1.8 ]] ; then
    echo "You must use Java 8"
    exit 1
fi

exit_script() {
  echo "Process terminated cleaning up resources"
  mv -f pom.xml.bak pom.xml
  rm -f pom.xml.versionsBackup
  trap - SIGINT SIGTERM # clear the trap
  kill -- -$$ # Sends SIGTERM to child/sub processes
}

trap exit_script SIGINT SIGTERM

DEPLOY_INSTALL=$1
SCALA_VERSION=$2
SPARK_VERSION=$3
# echo "command is: mvn -Pscala-$SCALA_VERSION -Pspark-$SPARK_VERSION -q -Dexec.executable=echo -Dexec.args='\${project.version}' --non-recursive exec:exec"
CURRENT_VERSION=$(mvn -Pscala-$SCALA_VERSION -Pspark-$SPARK_VERSION -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec)
#TARGET_DIR=spark-$SPARK_VERSION
if [[ $# -eq 4 ]] ; then
  ALT_DEPLOYMENT_REPOSITORY="-DaltDeploymentRepository=$4"
else
  ALT_DEPLOYMENT_REPOSITORY=""
fi

case $(sed --help 2>&1) in
  *GNU*) sed_i () { sed -i "$@"; };;
  *) sed_i () { sed -i '' "$@"; };;
esac

# backup files
cp pom.xml pom.xml.bak

# replace pom files with target scala version
sed_i "s/<artifactId>neo4j-dwh-connector<\/artifactId>/<artifactId>neo4j-dwh-connector_$SCALA_VERSION<\/artifactId>/" pom.xml
sed_i "s/<scala.binary.version \/>/<scala.binary.version>$SCALA_VERSION<\/scala.binary.version>/" pom.xml

# setting version
NEW_VERSION="${CURRENT_VERSION}_for_spark_${SPARK_VERSION}"
# echo "New version is $NEW_VERSION"
mvn -Pscala-$SCALA_VERSION -Pspark-$SPARK_VERSION versions:set -DnewVersion=$NEW_VERSION
# build
# echo "command is: mvn clean $DEPLOY_INSTALL -Pscala-$SCALA_VERSION -Pspark-$SPARK_VERSION -DskipTests $ALT_DEPLOYMENT_REPOSITORY"
mvn clean $DEPLOY_INSTALL -Pscala-$SCALA_VERSION -Pspark-$SPARK_VERSION -DskipTests $ALT_DEPLOYMENT_REPOSITORY

exit_script
