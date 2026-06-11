# 06 - Kafka 3.3.1（Zookeeper 模式）

> 选 ZK 模式而非 KRaft：3.3.1 时 KRaft 刚 GA，生态工具支持较差。Kafka 自带 ZK 脚本，无需单独装 Zookeeper。

## 1. 解压

```bash
cd /opt/bigdata/software/
tar -xzf kafka_2.12-3.3.1.tgz -C /opt/bigdata/service/
mv /opt/bigdata/service/kafka_2.12-3.3.1 /opt/bigdata/service/kafka
```

> 必须用 **scala 2.12** 版本（与 Spark 3.3.1 / Hive 3.1.3 的 scala 版本对齐）。

## 2. 目录

```bash
mkdir -p /opt/bigdata/data/kafka/{kafka-logs,zk}
mkdir -p /opt/bigdata/log/kafka
chown -R bigdata:bigdata /opt/bigdata/data/kafka /opt/bigdata/log/kafka
```

## 3. 环境变量（`~/.bashrc`）

```bash
# Kafka
export KAFKA_HOME=/opt/bigdata/service/kafka
export PATH=$KAFKA_HOME/bin:$PATH
# 让 nohup 启动的 kafka / zk 日志写到统一目录
export LOG_DIR=/opt/bigdata/log/kafka
```

## 4. 配置文件

**项目版本**：`config/kafka/`

| 文件 | 作用 |
|---|---|
| `zookeeper.properties` | 内嵌 ZK |
| `server.properties` | Broker |

**部署**：

```bash
cp ~/phone-analysis/config/kafka/{zookeeper.properties,server.properties} $KAFKA_HOME/config/
```

### 4.1 `zookeeper.properties`

```properties
dataDir=/opt/bigdata/data/kafka/zk
clientPort=2181
maxClientCnxns=64
admin.enableServer=false
```

### 4.2 `server.properties`

```properties
broker.id=0
listeners=PLAINTEXT://phone-analysis:9092
advertised.listeners=PLAINTEXT://phone-analysis:9092

log.dirs=/opt/bigdata/data/kafka/kafka-logs
num.partitions=3
default.replication.factor=1
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1

log.retention.hours=72
log.segment.bytes=1073741824
log.retention.check.interval.ms=300000

zookeeper.connect=phone-analysis:2181
zookeeper.connection.timeout.ms=18000

# 允许自动建 topic（开发期方便，生产建议关闭）
auto.create.topics.enable=true
delete.topic.enable=true
```

> `advertised.listeners` **必须** 是 `phone-analysis`，让远端 Spring Boot 客户端能解析回来。

## 5. 启停

```bash
# 先 ZK 再 Kafka
nohup zookeeper-server-start.sh $KAFKA_HOME/config/zookeeper.properties \
  > /opt/bigdata/log/kafka/zk.log 2>&1 &

nohup kafka-server-start.sh $KAFKA_HOME/config/server.properties \
  > /opt/bigdata/log/kafka/kafka.log 2>&1 &

# 停止（反向）
kafka-server-stop.sh
zookeeper-server-stop.sh
```

## 6. 项目 Topic 规划（建议）

```bash
kafka-topics.sh --bootstrap-server phone-analysis:9092 --create --topic phone_raw     --partitions 3 --replication-factor 1
kafka-topics.sh --bootstrap-server phone-analysis:9092 --create --topic phone_metric  --partitions 3 --replication-factor 1
kafka-topics.sh --bootstrap-server phone-analysis:9092 --create --topic phone_alert   --partitions 1 --replication-factor 1
```

## 7. 验证

```bash
kafka-topics.sh --bootstrap-server phone-analysis:9092 --list

# 生产
kafka-console-producer.sh --bootstrap-server phone-analysis:9092 --topic phone_raw
# 消费
kafka-console-consumer.sh --bootstrap-server phone-analysis:9092 --topic phone_raw --from-beginning
```
