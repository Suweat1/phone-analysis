# config/ — 集群组件配置 + 应用层覆盖配置

本目录有 **两类** 文件，作用不同：

## 一、集群组件配置（必须部署到运行机）

由 `scripts/deploy-config.sh` 在运行机端 `ln -sfn` 到各组件实际目录。

| 子目录 | 部署目标（运行机） | 文档 |
|---|---|---|
| `mysql/my.cnf` | `/etc/my.cnf` | [02-mysql.md](../docs/deploy/02-mysql.md) |
| `redis/redis.conf` | `${REDIS_HOME}/conf/redis.conf` | [03-redis.md](../docs/deploy/03-redis.md) |
| `hadoop/*.{sh,xml}` | `${HADOOP_HOME}/etc/hadoop/` | [04-hadoop.md](../docs/deploy/04-hadoop.md) |
| `hive/{hive-env.sh,hive-site.xml}` | `${HIVE_HOME}/conf/` | [05-hive.md](../docs/deploy/05-hive.md) |
| `kafka/{zookeeper.properties,server.properties}` | `${KAFKA_HOME}/config/` | [06-kafka.md](../docs/deploy/06-kafka.md) |
| `spark/{spark-env.sh,spark-defaults.conf}` | `${SPARK_HOME}/conf/` | [07-spark.md](../docs/deploy/07-spark.md) |
| `maven/settings.xml` | `~/.m2/settings.xml` | [09-maven.md](../docs/deploy/09-maven.md) |

## 二、应用层覆盖配置（**可选**，运行机生产覆盖用）

应用层配置已直接位于各模块的 `src/main/resources/`，跟随 jar / dist 一起 ship，本机能直接 `mvn package` / `npm run build`：

| 模块 | 默认位置（已打进产物） | 运行时是否需要外置覆盖 |
|---|---|---|
| `app` | `app/src/main/resources/application.yml` | 不需要；如需运行机覆盖，参考 `app/RUN.md §2` |
| `spark-etl` | `spark-etl/src/main/resources/application.properties` | 不需要；如需覆盖，参考 `spark-etl/RUN.md §1` |
| `web` | `web/.env`、`web/.env.development`、`web/.env.production` | Vue CLI 只读 `web/.env*`，不支持外置覆盖；改值直接改根目录 .env 文件 |

本目录下保留：

| 文件 | 作用 |
|---|---|
| `app/application.yml` | **运行机生产覆盖模板**：与 `app/src/main/resources/application.yml` 字段相同，但允许填运行机环境特有的值（不同密码、IP）。启动时显式 `--spring.config.additional-location=file:...` 才生效。 |
| `spark-etl/application.properties` | 同上语义；`PhoneConfig` 默认先找外部、回退到 classpath，所以 `spark-submit --files` 分发即生效。 |

> **重要**：如果你只用一套环境（本机=运行机或开发=生产相同），可以**完全忽略** `config/{app,spark-etl}/`，
> 所有真实配置只维护 `src/main/resources/` 里的那一份。

## 设计原则

1. **本机能编译**：所有产物自包含，本机 `mvn package` / `npm run build` 不依赖外置文件。
2. **运行机可覆盖**：通过 Spring `spring.config.additional-location` / Spark `--files` 机制按需注入差异。
3. **修改密码 / 主机名 / 路径**：开发期改 `src/main/resources/` 这一份即可；运行机特别覆盖才动 `config/{app,spark-etl}/`。
