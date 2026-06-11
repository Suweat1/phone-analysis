# config/ — 项目版配置文件

本目录集中存放 **所有组件 + 三个业务模块的真实配置**。部署文档 (`docs/deploy/`) 中每个组件章节都会显式告知如何把这里的文件 `cp` 或 `ln` 到目标路径。

## 设计原则

1. **唯一真实源**：URL / 密码 / 主机名 / 端口 / 路径 等真实参数 **只在此目录** 维护，业务代码通过各模块的 `Config` 类引用宏变量。
2. **不抄默认注释**：组件自带模板里的大段说明性注释不复制进来，只保留关键参数与必要的简短注释。
3. **修改密码 / 主机名 / 路径**：只动这里的对应文件，业务代码不动。

## 子目录

| 目录 | 部署目标（运行机） | 文档 |
|---|---|---|
| `mysql/my.cnf` | `/etc/my.cnf` | [02-mysql.md](../docs/deploy/02-mysql.md) |
| `redis/redis.conf` | `/opt/bigdata/service/redis/conf/redis.conf` | [03-redis.md](../docs/deploy/03-redis.md) |
| `hadoop/*.{sh,xml}` | `$HADOOP_HOME/etc/hadoop/` | [04-hadoop.md](../docs/deploy/04-hadoop.md) |
| `hive/{hive-env.sh,hive-site.xml}` | `$HIVE_HOME/conf/` | [05-hive.md](../docs/deploy/05-hive.md) |
| `kafka/{zookeeper.properties,server.properties}` | `$KAFKA_HOME/config/` | [06-kafka.md](../docs/deploy/06-kafka.md) |
| `spark/{spark-env.sh,spark-defaults.conf}` | `$SPARK_CONF_DIR/` | [07-spark.md](../docs/deploy/07-spark.md) |
| `maven/settings.xml` | `~/.m2/settings.xml` | [09-maven.md](../docs/deploy/09-maven.md) |
| `app/application.yml` | `app/` 模块运行目录或打包进 jar | — |
| `spark-etl/application.properties` | `--files` 分发到 executor | — |
| `web/{.env,config.js}` | `web/` 构建期或运行期注入 | — |

> 各子目录下当前为占位 `.gitkeep`；正式配置文件将在「写实际配置」步骤补齐。
