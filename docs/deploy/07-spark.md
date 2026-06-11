# 07 - Spark 3.3.1 on YARN

> 依赖：JDK / Hadoop / Hive 均已就绪。
> 使用 `spark-3.3.1-bin-hadoop3.tgz`（scala 2.12 版本，预编译 Hadoop 3）。

## 1. 解压

```bash
cd /opt/bigdata/software/
tar -xzf spark-3.3.1-bin-hadoop3.tgz -C /opt/bigdata/service/
mv /opt/bigdata/service/spark-3.3.1-bin-hadoop3 /opt/bigdata/service/spark
```

## 2. 目录

```bash
mkdir -p /opt/bigdata/log/spark
mkdir -p /opt/bigdata/service/spark/pids
chown -R bigdata:bigdata /opt/bigdata/service/spark /opt/bigdata/log/spark
```

## 3. 环境变量（`~/.bashrc`）

```bash
# Spark
export SPARK_HOME=/opt/bigdata/service/spark
export SPARK_CONF_DIR=$SPARK_HOME/conf
export PATH=$SPARK_HOME/bin:$SPARK_HOME/sbin:$PATH
# 与 Hadoop / Hive 集成
export HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop
export YARN_CONF_DIR=$HADOOP_HOME/etc/hadoop
```

## 4. JAR 兼容性

```bash
# 4.1 让 Spark on Hive 能用：把 Hive 的 hive-site.xml 链到 Spark conf
ln -sfn $HIVE_HOME/conf/hive-site.xml $SPARK_HOME/conf/hive-site.xml

# 4.2 MySQL JDBC（Spark 读 MySQL / Hive Metastore 备用）
cp /opt/bigdata/software/mysql-connector-java-8.0.31.jar $SPARK_HOME/jars/

# 4.3 Kafka 集成（Structured Streaming 用）
#     下载与 Spark/Scala 版本对齐的依赖：
#     spark-sql-kafka-0-10_2.12-3.3.1.jar
#     spark-token-provider-kafka-0-10_2.12-3.3.1.jar
#     kafka-clients-3.3.1.jar
#     commons-pool2-2.11.1.jar
cp /opt/bigdata/software/spark-kafka-deps/*.jar $SPARK_HOME/jars/
```

> 其他 jar 冲突（jline / netty 等）见 [11-jar-conflicts.md](./11-jar-conflicts.md)。

## 5. 配置文件

**项目版本**：`config/spark/`

| 文件 | 作用 |
|---|---|
| `spark-env.sh` | JAVA / Hadoop / YARN |
| `spark-defaults.conf` | 默认参数（master、history、shuffle） |

**部署**：

```bash
cp ~/phone-analysis/config/spark/{spark-env.sh,spark-defaults.conf} $SPARK_CONF_DIR/
chmod +x $SPARK_CONF_DIR/spark-env.sh
```

### 5.1 `spark-env.sh`

```bash
export JAVA_HOME=/opt/bigdata/service/jdk
export HADOOP_HOME=/opt/bigdata/service/hadoop
export HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop
export YARN_CONF_DIR=$HADOOP_HOME/etc/hadoop
export SPARK_LOG_DIR=/opt/bigdata/log/spark
export SPARK_PID_DIR=/opt/bigdata/service/spark/pids
```

### 5.2 `spark-defaults.conf`

```properties
spark.master                     yarn
spark.submit.deployMode          client
spark.serializer                 org.apache.spark.serializer.KryoSerializer

# Hive 集成
spark.sql.catalogImplementation  hive
spark.sql.warehouse.dir          /user/hive/warehouse

# 历史服务器
spark.eventLog.enabled           true
spark.eventLog.dir               hdfs://phone-analysis:9000/spark-history
spark.history.fs.logDirectory    hdfs://phone-analysis:9000/spark-history
spark.history.ui.port            18080

# 单机伪分布式资源建议
spark.executor.memory            2g
spark.executor.cores             2
spark.driver.memory              1g
spark.dynamicAllocation.enabled  false
```

## 6. HDFS 准备

```bash
hdfs dfs -mkdir -p /spark-history
hdfs dfs -chmod 777 /spark-history
```

## 7. 启停（仅 History Server，本身不需要常驻 master/worker，YARN 调度）

```bash
# 启动 History Server
start-history-server.sh
# 停止
stop-history-server.sh
```

## 8. 提交作业的标准命令

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  --class com.phone.etl.<MainClass> \
  --executor-memory 2g --executor-cores 2 --num-executors 2 \
  /opt/bigdata/data/jars/phone-etl.jar \
  <args...>
```

## 9. 验证

```bash
# spark-shell 直连 Hive
spark-shell --master yarn
scala> spark.sql("show databases").show
# 应能看到 phone_ods / phone_dwd / ...

# History Server Web
http://phone-analysis:18080/
```
