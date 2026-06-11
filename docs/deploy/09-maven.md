# 09 - Maven 3.6.x

> 用于运行机上构建 Spring Boot 应用与 Spark Scala 模块的 fat jar。

## 1. 解压

```bash
cd /opt/bigdata/software/
tar -xzf apache-maven-3.6.3-bin.tar.gz -C /opt/bigdata/service/
mv /opt/bigdata/service/apache-maven-3.6.3 /opt/bigdata/service/maven
```

## 2. 环境变量（`~/.bashrc`）

```bash
# Maven
export MAVEN_HOME=/opt/bigdata/service/maven
export PATH=$MAVEN_HOME/bin:$PATH
export MAVEN_OPTS="-Xms512m -Xmx2048m"
```

## 3. settings.xml（镜像加速）

**项目版本**：`config/maven/settings.xml`

**部署**：

```bash
mkdir -p ~/.m2
cp ~/phone-analysis/config/maven/settings.xml ~/.m2/settings.xml
```

关键内容：

```xml
<settings>
  <localRepository>/opt/bigdata/data/maven-repo</localRepository>
  <mirrors>
    <mirror>
      <id>aliyun-public</id>
      <name>aliyun public</name>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

```bash
mkdir -p /opt/bigdata/data/maven-repo
```

## 4. 验证

```bash
mvn -v       # Apache Maven 3.6.x，Java 显示 1.8.0_xxx
```

## 5. 项目构建命令

```bash
# Spring Boot 应用
cd ~/phone-analysis/app
mvn -DskipTests clean package

# Spark Scala 模块
cd ~/phone-analysis/spark-etl
mvn -DskipTests clean package
# 产物 fat jar → 复制到 /opt/bigdata/data/jars/ 供 spark-submit 使用
```
