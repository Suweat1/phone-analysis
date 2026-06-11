# 04 - Hadoop 3.3.4（HDFS + YARN 伪分布式）

> 单节点伪分布式：NameNode / DataNode / ResourceManager / NodeManager 全在 `phone-analysis` 这一台机器上。

## 1. 解压

```bash
cd /opt/bigdata/software/
tar -xzf hadoop-3.3.4.tar.gz -C /opt/bigdata/service/
mv /opt/bigdata/service/hadoop-3.3.4 /opt/bigdata/service/hadoop
```

## 2. 目录

```bash
mkdir -p /opt/bigdata/data/hadoop/{nn,dn,tmp}
mkdir -p /opt/bigdata/log/hadoop
chown -R bigdata:bigdata /opt/bigdata/data/hadoop /opt/bigdata/log/hadoop
```

## 3. 环境变量（`~/.bashrc`）

```bash
# Hadoop
export HADOOP_HOME=/opt/bigdata/service/hadoop
export HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop
export HADOOP_LOG_DIR=/opt/bigdata/log/hadoop
export HADOOP_PID_DIR=/opt/bigdata/service/hadoop/pids
export PATH=$HADOOP_HOME/bin:$HADOOP_HOME/sbin:$PATH

# YARN
export YARN_LOG_DIR=/opt/bigdata/log/hadoop
export YARN_PID_DIR=/opt/bigdata/service/hadoop/pids
```

## 4. 配置文件

**项目版本**：`config/hadoop/`，包含 5 个文件：

| 文件 | 作用 |
|---|---|
| `hadoop-env.sh` | 显式声明 JAVA_HOME（**必须**，否则 ssh 启动时找不到 JDK） |
| `core-site.xml` | NameNode 地址、临时目录 |
| `hdfs-site.xml` | NameNode / DataNode 本地存储、副本数 |
| `mapred-site.xml` | MR on YARN |
| `yarn-site.xml` | ResourceManager、辅助服务、内存 |

**部署**：

```bash
cp ~/phone-analysis/config/hadoop/*.{sh,xml} $HADOOP_CONF_DIR/
```

### 4.1 `hadoop-env.sh` 关键行

```bash
export JAVA_HOME=/opt/bigdata/service/jdk
export HADOOP_LOG_DIR=/opt/bigdata/log/hadoop
export HADOOP_PID_DIR=/opt/bigdata/service/hadoop/pids
```

### 4.2 `core-site.xml`

```xml
<configuration>
  <property>
    <name>fs.defaultFS</name>
    <value>hdfs://phone-analysis:9000</value>
  </property>
  <property>
    <name>hadoop.tmp.dir</name>
    <value>/opt/bigdata/data/hadoop/tmp</value>
  </property>
  <!-- Hive / Spark / Spring Boot 通过 bigdata 用户访问 HDFS 时的代理设置 -->
  <property>
    <name>hadoop.proxyuser.bigdata.hosts</name>
    <value>*</value>
  </property>
  <property>
    <name>hadoop.proxyuser.bigdata.groups</name>
    <value>*</value>
  </property>
</configuration>
```

### 4.3 `hdfs-site.xml`

```xml
<configuration>
  <property>
    <name>dfs.replication</name>
    <value>1</value>
  </property>
  <property>
    <name>dfs.namenode.name.dir</name>
    <value>file:///opt/bigdata/data/hadoop/nn</value>
  </property>
  <property>
    <name>dfs.datanode.data.dir</name>
    <value>file:///opt/bigdata/data/hadoop/dn</value>
  </property>
  <property>
    <name>dfs.permissions.enabled</name>
    <value>false</value>
  </property>
  <property>
    <name>dfs.webhdfs.enabled</name>
    <value>true</value>
  </property>
</configuration>
```

### 4.4 `mapred-site.xml`

```xml
<configuration>
  <property>
    <name>mapreduce.framework.name</name>
    <value>yarn</value>
  </property>
  <property>
    <name>mapreduce.application.classpath</name>
    <value>$HADOOP_MAPRED_HOME/share/hadoop/mapreduce/*:$HADOOP_MAPRED_HOME/share/hadoop/mapreduce/lib/*</value>
  </property>
</configuration>
```

### 4.5 `yarn-site.xml`

```xml
<configuration>
  <property>
    <name>yarn.resourcemanager.hostname</name>
    <value>phone-analysis</value>
  </property>
  <property>
    <name>yarn.nodemanager.aux-services</name>
    <value>mapreduce_shuffle</value>
  </property>
  <!-- 单机伪分布式：关掉物理/虚拟内存检查，避免 Spark Driver 被无脑杀掉 -->
  <property>
    <name>yarn.nodemanager.pmem-check-enabled</name>
    <value>false</value>
  </property>
  <property>
    <name>yarn.nodemanager.vmem-check-enabled</name>
    <value>false</value>
  </property>
  <property>
    <name>yarn.nodemanager.resource.memory-mb</name>
    <value>6144</value>
  </property>
  <property>
    <name>yarn.nodemanager.resource.cpu-vcores</name>
    <value>4</value>
  </property>
  <!-- 让 Spark 作业能读到 Hadoop 类路径 -->
  <property>
    <name>yarn.application.classpath</name>
    <value>$HADOOP_CONF_DIR,$HADOOP_COMMON_HOME/share/hadoop/common/*,$HADOOP_COMMON_HOME/share/hadoop/common/lib/*,$HADOOP_HDFS_HOME/share/hadoop/hdfs/*,$HADOOP_HDFS_HOME/share/hadoop/hdfs/lib/*,$HADOOP_YARN_HOME/share/hadoop/yarn/*,$HADOOP_YARN_HOME/share/hadoop/yarn/lib/*,$HADOOP_MAPRED_HOME/share/hadoop/mapreduce/*,$HADOOP_MAPRED_HOME/share/hadoop/mapreduce/lib/*</value>
  </property>
</configuration>
```

## 5. workers 文件

`$HADOOP_CONF_DIR/workers` 只写一行：

```
phone-analysis
```

## 6. 首次格式化（只做一次！）

```bash
hdfs namenode -format
```

> **重新格式化** 会让旧 DataNode 的 clusterID 对不上而启动失败。如需重置，先 `rm -rf /opt/bigdata/data/hadoop/{nn,dn,tmp}/*` 再格式化。

## 7. 启停

```bash
# 启动 HDFS
start-dfs.sh
# 启动 YARN
start-yarn.sh

# 停止（反向）
stop-yarn.sh
stop-dfs.sh
```

`jps` 应能看到：`NameNode`、`DataNode`、`SecondaryNameNode`、`ResourceManager`、`NodeManager`。

## 8. 初始化业务目录

```bash
hdfs dfs -mkdir -p /user/bigdata
hdfs dfs -mkdir -p /user/hive/warehouse
hdfs dfs -mkdir -p /phone-analysis/{raw,ods,dwd,dws,ads}
hdfs dfs -chmod -R 777 /user/hive/warehouse
hdfs dfs -chmod -R 777 /phone-analysis
```

## 验证

- Web UI：`http://phone-analysis:9870/`（NameNode）、`http://phone-analysis:8088/`（YARN）
- `hdfs dfs -ls /` 能列出目录
- `yarn node -list` 显示一个 RUNNING 节点
