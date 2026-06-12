# 11 - JAR 冲突清单与覆盖方案

> Hadoop 3.3.4 + Hive 3.1.3 + Spark 3.3.1 + Kafka 3.3.1 + MySQL 8.0.31 各自携带的 jar 之间存在多组冲突，单机伪分布式环境中常以「**覆盖 / 删除 / 补充**」三种方式解决。本表为本项目实操确认的最小集。

## 1. Hive ↔ Hadoop

| 冲突包 | Hive 自带 | Hadoop 自带 | 处理 |
|---|---|---|---|
| `guava` | 19.0 | 27.0-jre | 删 Hive 的，复制 Hadoop 的到 `$HIVE_HOME/lib/` |
| `jline` | 3.x（在 Hive lib） | 2.x（在 yarn lib） | 启动 Hive CLI 报版本冲突时，把 `$HADOOP_HOME/share/hadoop/yarn/lib/jline-*.jar` 移走 |
| `protobuf-java` | 2.5.0 | 2.5.0 | 一致，无需处理 |

```bash
# guava
rm -f $HIVE_HOME/lib/guava-19.0.jar
cp $HADOOP_HOME/share/hadoop/common/lib/guava-27.0-jre.jar $HIVE_HOME/lib/
```

## 2. Hive ↔ MySQL JDBC

Hive 不自带 MySQL Connector，必须手工补：

```bash
cp /opt/bigdata/software/mysql-connector-java-8.0.31.jar $HIVE_HOME/lib/
```

> 注意 MySQL 8 的驱动类是 `com.mysql.cj.jdbc.Driver`（**不是** `com.mysql.jdbc.Driver`），`hive-site.xml` 中的 `ConnectionDriverName` 已按此设置。

## 3. Spark ↔ Hive

Spark 3.3.1 内嵌的 Hive client 是 **2.3.9**，运行机 Metastore 是 **3.1.3**。直接连会报：

```
java.lang.IllegalArgumentException: Builtin jars can only be used when
hive execution version == hive metastore version. Execution: 2.3.9 != Metastore: 3.1.3.
```

正解：让 Spark 加载真实的 Hive 3.1.3 lib 作 metastore client。`config/spark/spark-defaults.conf` 已写：

```
spark.sql.hive.metastore.version    3.1.3
spark.sql.hive.metastore.jars       path
spark.sql.hive.metastore.jars.path  file:///opt/bigdata/service/hive/lib/*.jar
```

注意：
- `spark.sql.hive.metastore.jars=builtin` **只能** 在 version=2.3.9 时用，否则版本不匹配。
- 路径必须 `file://` 协议且以 `/*.jar` 结尾（通配整个目录）。
- 同时配上 `hive-site.xml` 和 MySQL JDBC：

```bash
ln -sfn $HIVE_HOME/conf/hive-site.xml $SPARK_HOME/conf/hive-site.xml
cp /opt/bigdata/software/mysql-connector-java-8.0.31.jar $SPARK_HOME/jars/
```

`spark-defaults.conf` 中已设置 `spark.sql.catalogImplementation=hive`。

> 不要把 `$HIVE_HOME/lib/*` 整个塞进 `$SPARK_HOME/jars/`，会引入 `datanucleus-*` 与 Spark 自带 `derby` 的循环引用，导致 Driver 启动报 `java.lang.IncompatibleClassChangeError`。**正确方式是用上面的 `metastore.jars.path` 让 Spark 在隔离 classloader 里加载，不污染主 classpath。**

## 4. Spark ↔ Kafka

Spark Structured Streaming 集成 Kafka 需要补 4 个 jar（版本必须严格对齐）：

| jar | 版本 |
|---|---|
| `spark-sql-kafka-0-10_2.12` | 3.3.1 |
| `spark-token-provider-kafka-0-10_2.12` | 3.3.1 |
| `kafka-clients` | 3.3.1 |
| `commons-pool2` | 2.11.1 |

放置：

```bash
cp /opt/bigdata/software/spark-kafka-deps/*.jar $SPARK_HOME/jars/
```

或 `spark-submit` 时用 `--packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.3.1`（运行机能联网时更简单）。

## 5. Spark ↔ Hadoop (netty)

Spark 3.3.1 自带 `netty-all-4.1.74`，与 Hadoop 3.3.4 的 `netty-3.10.6` 在 YARN classpath 中共存通常无冲突；**只在** `yarn-site.xml` 的 `yarn.application.classpath` 把 Hadoop 类路径放在 Spark 自带 jar 之后才会出现 `NoSuchMethodError`。本项目 `yarn.application.classpath` 已避免该顺序问题。

## 6. Hadoop ↔ JDK 1.8（slf4j 与 log4j）

Hadoop / Hive / Spark 均自带各自版本的 `slf4j` 和 `log4j`，**不要** 跨组件复制日志相关 jar。每个组件 `lib/` 内部已经一致；跨组件覆盖会触发 `ClassCastException`：

> 反例：把 Spark 的 `slf4j-api-1.7.30.jar` 复制到 Hive lib 会让 `schematool` 直接报错。

## 7. Spring Boot ↔ Hive JDBC

Spring Boot 应用通过 `hive-jdbc-3.1.3` 连 HiveServer2 时，会拉入 `org.apache.hive:hive-service-rpc` 等一连串依赖，常与 Spring Boot 的 `logback` / `jackson` 冲突。建议 Maven 依赖里 **只引** 一个独立的 `hive-jdbc-standalone` jar：

```xml
<dependency>
  <groupId>org.apache.hive</groupId>
  <artifactId>hive-jdbc</artifactId>
  <version>3.1.3</version>
  <classifier>standalone</classifier>
  <exclusions>
    <exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-log4j12</artifactId></exclusion>
    <exclusion><groupId>org.eclipse.jetty.aggregate</groupId><artifactId>*</artifactId></exclusion>
  </exclusions>
</dependency>
```

## 8. 常见报错速查

| 报错关键字 | 根因 | 处理 |
|---|---|---|
| `Builtin jars can only be used when hive execution version == hive metastore version. Execution: 2.3.9 != Metastore: 3.1.3` | Spark 内置 Hive 2.3.9 ≠ 运行机 Hive 3.1.3 | §3 改 `metastore.jars=path` 指向 Hive lib |
| `NoSuchMethodError: com.google.common.base.Preconditions` | Hive guava 19 vs Hadoop guava 27 | 替换 §1 |
| `unsupported major.minor version 55.0` | 误用了 JDK 11 | 检查 `java -version` |
| `Public Key Retrieval is not allowed` | MySQL 8 + `useSSL=false` 缺参数 | URL 加 `allowPublicKeyRetrieval=true` |
| `Could not initialize class org.apache.kafka.clients.producer.KafkaProducer` | Spark 缺 kafka-clients | §4 补 jar |
| `org.apache.hadoop.hive.metastore.api.MetaException: Version information not found` | Hive schema 未初始化 | `schematool -dbType mysql -initSchema` |
| `Connection refused: phone-analysis/127.0.1.1:9092` | Kafka `advertised.listeners` 写成 localhost | 改为 `phone-analysis` |
| `Permission denied: user=dr.who` | HDFS 用错用户 | core-site.xml 已配 proxyuser，否则用 `hdfs dfs -chmod 777` 临时放行 |

## 9. 整理 jar 改动的纪律

所有手工覆盖 / 删除的 jar，**必须** 在本文件追加一行说明（哪个组件、做了什么、为什么），以便升级时能复现。建议在每个组件 lib 下创建一个 `OVERRIDE.md`，记录差异。
