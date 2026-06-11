# phone-analysis 部署文档

本目录为 phone-analysis 项目在 **单台 Ubuntu 20.04 虚拟机（glibc 2.31）** 上以 **伪分布式** 形式部署所有大数据组件的操作手册。所有组件以 **二进制预编译包** 部署（Redis 例外，源码编译），**禁止使用 systemd**。

## 阅读顺序

| 序号 | 文档 | 内容 |
|---|---|---|
| 00 | [00-system-prepare.md](./00-system-prepare.md) | 系统初始化：用户 / 主机名 / sudo 免密 / ssh 免密 / 必需 .so 库 / 目录约定 |
| 01 | [01-jdk.md](./01-jdk.md) | JDK 1.8 二进制 + 软链接 |
| 02 | [02-mysql.md](./02-mysql.md) | MySQL 8.0.31 二进制 |
| 03 | [03-redis.md](./03-redis.md) | Redis 6.2.7 源码编译 |
| 04 | [04-hadoop.md](./04-hadoop.md) | Hadoop 3.3.4 HDFS + YARN 伪分布式 |
| 05 | [05-hive.md](./05-hive.md) | Hive 3.1.3 Remote Metastore + HiveServer2 |
| 06 | [06-kafka.md](./06-kafka.md) | Kafka 3.3.1 + 自带 Zookeeper |
| 07 | [07-spark.md](./07-spark.md) | Spark 3.3.1 on YARN + Spark on Hive |
| 08 | [08-node.md](./08-node.md) | Node 14+（前端构建） |
| 09 | [09-maven.md](./09-maven.md) | Maven 3.6.x（后端 / Spark 构建） |
| 10 | [10-startup-order.md](./10-startup-order.md) | 启动 / 停止顺序与一键脚本建议 |
| 11 | [11-jar-conflicts.md](./11-jar-conflicts.md) | Hadoop / Hive / Spark 之间常见 jar 冲突清单与覆盖方案 |

## 全局约定速查

| 项目 | 值 |
|---|---|
| 运行用户 | `bigdata`（需 sudo 免密 + ssh localhost 免密） |
| 主机名 | `phone-analysis` |
| 软件包目录 | `/opt/bigdata/software/` |
| 服务目录 | `/opt/bigdata/service/` |
| 日志目录 | `/opt/bigdata/log/` |
| 数据目录 | `/opt/bigdata/data/` |
| 环境变量位置 | `~/.bashrc`（**禁止** 写到 `/etc/profile.d/*.sh`） |
| MySQL root 密码 | `123456` |
| Hive 元数据库密码 | `123456` |
| Redis 密码 | `redis123` |
| 进程守护 | `nohup` / 组件自带 `*.sh`（**禁止** systemd） |

## 配置文件管理

- 所有配置文件的「**项目版本**」存放在仓库 `config/<组件名>/` 下，例如 `config/mysql/my.cnf`、`config/hadoop/hdfs-site.xml`。
- 部署时由运行机 `git pull` 后，**软链接或复制** 到组件实际配置目录（`service/<组件>/conf/`、`/etc/my.cnf` 等），具体路径见各组件文档。
- 配置文件 **只保留关键参数与必要注释**，模板里大段说明性注释不抄录。

## 端口规划

| 组件 | 端口 |
|---|---|
| MySQL | 3306 |
| Redis | 6379 |
| HDFS NameNode RPC | 9000 |
| HDFS NameNode Web | 9870 |
| YARN ResourceManager Web | 8088 |
| Hive Metastore | 9083 |
| HiveServer2 | 10000（thrift）/ 10002（web） |
| Zookeeper | 2181 |
| Kafka Broker | 9092 |
| Spark History Server | 18080 |
| Spring Boot 应用 | 8080 |
| Vue 前端（开发） | 8081 |
