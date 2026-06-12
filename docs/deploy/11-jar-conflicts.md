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

Spark 3.3.1 内嵌的 Hive client 是 **2.3.9**，运行机 Metastore 是 **3.1.3**。

**Hive thrift 协议向后兼容：用 2.3.9 client 连 3.1.3 metastore 是可以的**，这是 Spark 官方推荐做法。
直接 `spark.sql.hive.metastore.version=2.3.9 + jars=builtin` 即可——这是默认值，不需要任何配置。

```
# config/spark/spark-defaults.conf
spark.sql.hive.metastore.version 2.3.9
spark.sql.hive.metastore.jars    builtin
```

⚠️ **不要** 配成 `version=3.1.3 + jars=path`。Spark 会把 `$HIVE_HOME/lib/` 下 200+ Hive 3.1.3 jar 加载到隔离 classloader 当 Hive client。在 8GB VM 这套庞大 client 又慢又脆，碰到 `getTable` 这类常见调用就 read timeout（默认 10 分钟），让作业看起来一直卡死。

如果在某些边缘场景确实必须 3.1.3 client（极少见），那种情况下要：
- VM 内存 ≥ 16GB；
- 同时配 `spark.sql.hive.metastore.sharedPrefixes` 把 hive/hadoop 共享类排除以避免反射坑。

`hive-site.xml` 和 MySQL JDBC 仍要在 Spark 端可见：

```bash
ln -sfn $HIVE_HOME/conf/hive-site.xml $SPARK_HOME/conf/hive-site.xml
cp /opt/bigdata/software/mysql-connector-java-8.0.31.jar $SPARK_HOME/jars/
```

> 不要把 `$HIVE_HOME/lib/*` 整个塞进 `$SPARK_HOME/jars/`，会引入 `datanucleus-*` 与 Spark 自带 `derby` 的循环引用，导致 Driver 启动报 `java.lang.IncompatibleClassChangeError`。

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

> ⚠️ 历史误区：早期建议「用 `hive-jdbc:standalone` classifier 避免冲突」。**这是错的**——standalone 是 fat jar，里面 shade 了一份旧 Tomcat / Jetty / Jasper，反而与 Spring Boot 2.7 自带 Tomcat 9.0.x 撞车。典型报错：
>
> ```
> AbstractMethodError: org.apache.jasper.servlet.TldScanner$TldScannerCallback
>   .scan(Lorg/apache/tomcat/Jar;Ljava/lang/String;Z)V
> ```
>
> 原因是 standalone 里那份旧 jasper 实现的 `JarScannerCallback#scan` 是旧签名，新版 Tomcat 的抽象方法找不到 override。

**正确做法**：用普通 `hive-jdbc`，精准排除 web 容器 / 日志桥 / yarn-rm / servlet 几类依赖：

```xml
<dependency>
  <groupId>org.apache.hive</groupId>
  <artifactId>hive-jdbc</artifactId>
  <version>3.1.3</version>
  <exclusions>
    <!-- 日志桥：Spring Boot 用 logback，hive 拉来的 log4j 全踢 -->
    <exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-log4j12</artifactId></exclusion>
    <exclusion><groupId>org.apache.logging.log4j</groupId><artifactId>*</artifactId></exclusion>
    <exclusion><groupId>log4j</groupId><artifactId>log4j</artifactId></exclusion>
    <!-- web 容器：spring-boot-starter-web 已经提供 Tomcat 9.0.x -->
    <exclusion><groupId>org.eclipse.jetty</groupId><artifactId>*</artifactId></exclusion>
    <exclusion><groupId>org.eclipse.jetty.aggregate</groupId><artifactId>*</artifactId></exclusion>
    <exclusion><groupId>org.apache.tomcat</groupId><artifactId>*</artifactId></exclusion>
    <exclusion><groupId>org.apache.tomcat.embed</groupId><artifactId>*</artifactId></exclusion>
    <!-- 伪分布式不需要 yarn rm -->
    <exclusion>
      <groupId>org.apache.hadoop</groupId>
      <artifactId>hadoop-yarn-server-resourcemanager</artifactId>
    </exclusion>
    <!-- servlet API 用 starter-web 的 -->
    <exclusion><groupId>javax.servlet</groupId><artifactId>*</artifactId></exclusion>
    <exclusion><groupId>javax.servlet.jsp</groupId><artifactId>*</artifactId></exclusion>
  </exclusions>
</dependency>
```

`app/pom.xml` 已按此配置，改动时不要再回退到 `<classifier>standalone</classifier>`。

## 8. 常见报错速查

| 报错关键字 | 根因 | 处理 |
|---|---|---|
| `Builtin jars can only be used when hive execution version == hive metastore version. Execution: 2.3.9 != Metastore: 3.1.3` | 显式写了 `metastore.version=3.1.3` 又用 `jars=builtin`（builtin 只能配 2.3.9） | §3 把 version 改回 2.3.9，jars 保持 builtin（thrift 协议兼容） |
| Spark 启动后 metastore 调用永远卡 10 分钟超时 / `Read timed out` | `metastore.jars=path` 把 200+ Hive 3.1.3 jar 加载隔离 classloader，对 8GB VM 太重 | §3 同上：换回 2.3.9 builtin |
| `DROP TABLE IF EXISTS` 在 Spark / beeline 卡几分钟，metastore JStack 显示 `ObjectStore.dropConstraint` 在 MySQL 上 read 卡死 | Hive 3.1.3 [HIVE-21563](https://issues.apache.org/jira/browse/HIVE-21563)：`KEY_CONSTRAINTS` 表 `PARENT_TBL_ID/CHILD_TBL_ID` 没索引，DROP 会全表扫 | 在 hive_metastore 库执行：`ALTER TABLE KEY_CONSTRAINTS ADD INDEX CONSTRAINTS_PARENT_TBL_ID_INDEX (PARENT_TBL_ID), ADD INDEX CONSTRAINTS_CHILD_TBL_ID_INDEX (CHILD_TBL_ID);`（详见 [05-hive.md §6.1](./05-hive.md)） |
| `NoSuchMethodError: com.google.common.base.Preconditions` | Hive guava 19 vs Hadoop guava 27 | 替换 §1 |
| `unsupported major.minor version 55.0` | 误用了 JDK 11 | 检查 `java -version` |
| `Public Key Retrieval is not allowed` | MySQL 8 + `useSSL=false` 缺参数 | URL 加 `allowPublicKeyRetrieval=true` |
| `Could not initialize class org.apache.kafka.clients.producer.KafkaProducer` | Spark 缺 kafka-clients | §4 补 jar |
| `org.apache.hadoop.hive.metastore.api.MetaException: Version information not found` | Hive schema 未初始化 | `schematool -dbType mysql -initSchema` |
| `Connection refused: phone-analysis/127.0.1.1:9092` | Kafka `advertised.listeners` 写成 localhost | 改为 `phone-analysis` |
| `Permission denied: user=dr.who` | HDFS 用错用户 | core-site.xml 已配 proxyuser，否则用 `hdfs dfs -chmod 777` 临时放行 |
| `AbstractMethodError: TldScannerCallback.scan(Lorg/apache/tomcat/Jar;...)V` | `hive-jdbc` 用了 `standalone` classifier，shade 进旧 Tomcat 与 Spring Boot 自带 Tomcat 9.x 接口不兼容 | §7 改普通 `hive-jdbc` + 精准 exclusions |

## 9. 整理 jar 改动的纪律

所有手工覆盖 / 删除的 jar，**必须** 在本文件追加一行说明（哪个组件、做了什么、为什么），以便升级时能复现。建议在每个组件 lib 下创建一个 `OVERRIDE.md`，记录差异。
