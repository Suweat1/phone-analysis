# Hive 3.1.3 — phone-analysis 项目环境
# 部署目标：$HIVE_HOME/conf/hive-env.sh
# 详细说明见 docs/deploy/05-hive.md

export HADOOP_HOME=/opt/bigdata/service/hadoop
export HIVE_CONF_DIR=/opt/bigdata/service/hive/conf
export HIVE_AUX_JARS_PATH=/opt/bigdata/service/hive/lib

# JVM 堆
export HADOOP_HEAPSIZE=1024

# 日志
export HIVE_LOG_DIR=/opt/bigdata/log/hive
