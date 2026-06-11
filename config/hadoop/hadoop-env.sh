# Hadoop 3.3.4 — phone-analysis 项目环境
# 部署目标：$HADOOP_HOME/etc/hadoop/hadoop-env.sh
# 详细说明见 docs/deploy/04-hadoop.md

# JAVA_HOME：必须显式声明，否则 ssh 启动 DataNode/NodeManager 时找不到 JDK
export JAVA_HOME=/opt/bigdata/service/jdk

# 日志与 pid
export HADOOP_LOG_DIR=/opt/bigdata/log/hadoop
export HADOOP_PID_DIR=/opt/bigdata/service/hadoop/pids

# 运行用户（伪分布式单机环境）
export HDFS_NAMENODE_USER=bigdata
export HDFS_DATANODE_USER=bigdata
export HDFS_SECONDARYNAMENODE_USER=bigdata
export YARN_RESOURCEMANAGER_USER=bigdata
export YARN_NODEMANAGER_USER=bigdata

# JVM 堆（单机伪分布式，节制即可）
export HADOOP_HEAPSIZE_MAX=1024
export HADOOP_HEAPSIZE_MIN=512
