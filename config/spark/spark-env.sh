#!/usr/bin/env bash
# Spark 3.3.1 — phone-analysis
# 部署目标：$SPARK_HOME/conf/spark-env.sh
# 详细说明见 docs/deploy/07-spark.md

export JAVA_HOME=/opt/bigdata/service/jdk
export HADOOP_HOME=/opt/bigdata/service/hadoop
export HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop
export YARN_CONF_DIR=$HADOOP_HOME/etc/hadoop

export SPARK_LOG_DIR=/opt/bigdata/log/spark
export SPARK_PID_DIR=/opt/bigdata/service/spark/pids

# History Server JVM
export SPARK_HISTORY_OPTS="-Dspark.history.ui.port=18080 -Dspark.history.retainedApplications=30 -Dspark.history.fs.logDirectory=hdfs://phone-analysis:9000/spark-history"

# Driver / Executor 类路径补 MySQL JDBC
export SPARK_CLASSPATH=$SPARK_HOME/jars/mysql-connector-java-8.0.31.jar
