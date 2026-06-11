# 05 - Hive 3.1.3（Remote Metastore + HiveServer2）

> 依赖：[02-mysql.md](./02-mysql.md) 已创建 `hive_metastore` 库 + `hive/123456` 账号；[04-hadoop.md](./04-hadoop.md) 已正常运行。

## 1. 解压

```bash
cd /opt/bigdata/software/
tar -xzf apache-hive-3.1.3-bin.tar.gz -C /opt/bigdata/service/
mv /opt/bigdata/service/apache-hive-3.1.3-bin /opt/bigdata/service/hive
```

## 2. 目录

```bash
mkdir -p /opt/bigdata/log/hive
mkdir -p /opt/bigdata/service/hive/pids
chown -R bigdata:bigdata /opt/bigdata/service/hive /opt/bigdata/log/hive
```

## 3. 环境变量（`~/.bashrc`）

```bash
# Hive
export HIVE_HOME=/opt/bigdata/service/hive
export HIVE_CONF_DIR=$HIVE_HOME/conf
export PATH=$HIVE_HOME/bin:$PATH
```

## 4. JAR 兼容性处理（**必做**）

Hive 3.1.3 与 Hadoop 3.3.4 之间存在 `guava` 与 `jline` 版本冲突；同时缺 MySQL JDBC 驱动。

```bash
# 4.1 guava：用 Hadoop 的版本覆盖 Hive 的（Hive 自带 19.0 太老）
rm -f $HIVE_HOME/lib/guava-19.0.jar
cp $HADOOP_HOME/share/hadoop/common/lib/guava-27.0-jre.jar $HIVE_HOME/lib/

# 4.2 jline：Hadoop 自带 jline-2.x，会与 Beeline 冲突；保留 Hive 内的 3.x 即可
#     若启动报 jline 版本冲突，从 HADOOP_HOME 临时移走
#     mv $HADOOP_HOME/share/hadoop/yarn/lib/jline-*.jar /tmp/

# 4.3 MySQL Connector
cp /opt/bigdata/software/mysql-connector-java-8.0.31.jar $HIVE_HOME/lib/
```

> 关于其余 jar 冲突的完整清单见 [11-jar-conflicts.md](./11-jar-conflicts.md)。

## 5. 配置文件

**项目版本**：`config/hive/`

| 文件 | 作用 |
|---|---|
| `hive-env.sh` | 声明 HADOOP_HOME / HIVE_CONF_DIR |
| `hive-site.xml` | Metastore + HiveServer2 + JDBC |

**部署**：

```bash
cp ~/phone-analysis/config/hive/{hive-env.sh,hive-site.xml} $HIVE_CONF_DIR/
```

### 5.1 `hive-env.sh`

```bash
export HADOOP_HOME=/opt/bigdata/service/hadoop
export HIVE_CONF_DIR=/opt/bigdata/service/hive/conf
export HIVE_AUX_JARS_PATH=/opt/bigdata/service/hive/lib
```

### 5.2 `hive-site.xml`

```xml
<configuration>
  <!-- 元数据库：MySQL -->
  <property>
    <name>javax.jdo.option.ConnectionURL</name>
    <value>jdbc:mysql://phone-analysis:3306/hive_metastore?useSSL=false&amp;useUnicode=true&amp;characterEncoding=UTF-8&amp;serverTimezone=Asia/Shanghai&amp;allowPublicKeyRetrieval=true</value>
  </property>
  <property>
    <name>javax.jdo.option.ConnectionDriverName</name>
    <value>com.mysql.cj.jdbc.Driver</value>
  </property>
  <property>
    <name>javax.jdo.option.ConnectionUserName</name>
    <value>hive</value>
  </property>
  <property>
    <name>javax.jdo.option.ConnectionPassword</name>
    <value>123456</value>
  </property>

  <!-- 仓库目录 -->
  <property>
    <name>hive.metastore.warehouse.dir</name>
    <value>/user/hive/warehouse</value>
  </property>

  <!-- Remote Metastore：Spark / HS2 通过 thrift 连这里 -->
  <property>
    <name>hive.metastore.uris</name>
    <value>thrift://phone-analysis:9083</value>
  </property>

  <!-- HiveServer2 -->
  <property>
    <name>hive.server2.thrift.bind.host</name>
    <value>phone-analysis</value>
  </property>
  <property>
    <name>hive.server2.thrift.port</name>
    <value>10000</value>
  </property>
  <property>
    <name>hive.server2.enable.doAs</name>
    <value>false</value>
  </property>

  <!-- 关闭不必要的检查 -->
  <property>
    <name>hive.metastore.schema.verification</name>
    <value>false</value>
  </property>
  <property>
    <name>datanucleus.schema.autoCreateAll</name>
    <value>true</value>
  </property>
</configuration>
```

## 6. 初始化元数据库

```bash
schematool -dbType mysql -initSchema
```

成功后 MySQL 的 `hive_metastore` 库中会出现 70+ 张表。

## 7. 启停（无 systemd）

```bash
# 启动 Metastore（必须先起，给 HS2 与 Spark 用）
nohup hive --service metastore > /opt/bigdata/log/hive/metastore.log 2>&1 &

# 启动 HiveServer2（供 Beeline / Spring Boot JDBC 使用）
nohup hive --service hiveserver2 > /opt/bigdata/log/hive/hiveserver2.log 2>&1 &
```

停止：

```bash
# 找 PID 杀掉
ps -ef | grep -E 'HiveMetaStore|HiveServer2' | grep -v grep
kill <pid>
```

## 8. 验证

```bash
# 端口
ss -ltnp | grep -E '9083|10000'

# Beeline 连接
beeline -u 'jdbc:hive2://phone-analysis:10000/default' -n bigdata
0: jdbc:hive2://phone-analysis:10000/default> show databases;
```

## 9. 创建项目库

```sql
CREATE DATABASE IF NOT EXISTS phone_ods;
CREATE DATABASE IF NOT EXISTS phone_dwd;
CREATE DATABASE IF NOT EXISTS phone_dws;
CREATE DATABASE IF NOT EXISTS phone_ads;
```
