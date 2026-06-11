# 10 - 启动 / 停止顺序

> 所有组件均以 `bigdata` 用户身份启动；**禁止 systemd**。建议把下面的命令封装成两个脚本 `scripts/start-all.sh` / `scripts/stop-all.sh`。

## 启动顺序（依赖 → 被依赖）

```
1. MySQL                ←  Hive 元数据
2. Redis                ←  Spring Boot 缓存
3. HDFS (NameNode/DN)   ←  Hive / Spark 数据存储
4. YARN (RM/NM)         ←  Spark 调度
5. Hive Metastore       ←  Spark on Hive / HiveServer2
6. HiveServer2          ←  Spring Boot JDBC / Beeline
7. Zookeeper            ←  Kafka
8. Kafka                ←  Spring Boot 生产者 / Spark Streaming
9. Spark History Server ←  Web 查看历史作业
10. Spring Boot 应用
```

## 启动命令汇总

```bash
# 1. MySQL
mysqld_safe --defaults-file=/etc/my.cnf --user=bigdata &

# 2. Redis
redis-server /opt/bigdata/service/redis/conf/redis.conf

# 3-4. Hadoop
start-dfs.sh
start-yarn.sh

# 5. Hive Metastore
nohup hive --service metastore > /opt/bigdata/log/hive/metastore.log 2>&1 &

# 6. HiveServer2
nohup hive --service hiveserver2 > /opt/bigdata/log/hive/hiveserver2.log 2>&1 &

# 7. Zookeeper（Kafka 自带）
nohup zookeeper-server-start.sh $KAFKA_HOME/config/zookeeper.properties \
  > /opt/bigdata/log/kafka/zk.log 2>&1 &

# 8. Kafka
nohup kafka-server-start.sh $KAFKA_HOME/config/server.properties \
  > /opt/bigdata/log/kafka/kafka.log 2>&1 &

# 9. Spark History Server
start-history-server.sh

# 10. Spring Boot
cd ~/phone-analysis/app
nohup java -jar target/app.jar \
  --spring.profiles.active=prod \
  > /opt/bigdata/log/app/app.log 2>&1 &
```

## 停止顺序（启动顺序的反序）

```bash
# 10. Spring Boot
kill $(cat /opt/bigdata/service/app/app.pid)   # 若未写 pid 文件，则 ps -ef | grep java

# 9. Spark History Server
stop-history-server.sh

# 8. Kafka
kafka-server-stop.sh

# 7. Zookeeper
zookeeper-server-stop.sh

# 6. HiveServer2/ 5. Metastore
ps -ef | grep -E 'HiveServer2|HiveMetaStore' | grep -v grep | awk '{print $2}' | xargs -r kill

# 4-3. Hadoop
stop-yarn.sh
stop-dfs.sh

# 2. Redis
redis-cli -a redis123 shutdown

# 1. MySQL
mysqladmin -uroot -p123456 shutdown
```

## 健康检查 1 行命令

```bash
jps -l && ss -ltn | grep -E '3306|6379|9000|8088|9870|9083|10000|2181|9092|18080|8080'
```

预期看到的 jps 进程：

```
NameNode
DataNode
SecondaryNameNode
ResourceManager
NodeManager
HiveMetaStore（RunJar）
HiveServer2 （RunJar）
QuorumPeerMain （ZK）
Kafka
HistoryServer
```

> MySQL / Redis / Spring Boot 不在 jps 中；前两个用 `ss -ltn` 看端口，后者用进程列表。
