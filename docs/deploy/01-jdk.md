# 01 - JDK 1.8

## 1. 安装

从 Oracle / 镜像站获取 `jdk-8uXXX-linux-x64.tar.gz`，例如 `jdk-8u371-linux-x64.tar.gz`，放到 `/opt/bigdata/software/`。

```bash
cd /opt/bigdata/software/
tar -xzf jdk-8u371-linux-x64.tar.gz -C /opt/bigdata/service/
```

## 2. 软链接（重要）

> JDK 1.8 小版本号迭代频繁，所有下游组件（Hadoop / Hive / Spark / Kafka / Spring）都通过 **软链接** 引用 JDK，避免每次升级都改环境变量。

```bash
cd /opt/bigdata/service/
ln -sfn jdk1.8.0_371 jdk
```

后续 `JAVA_HOME` 固定指向 `/opt/bigdata/service/jdk`。

## 3. 环境变量（追加到 `~/.bashrc`）

```bash
# JDK
export JAVA_HOME=/opt/bigdata/service/jdk
export PATH=$JAVA_HOME/bin:$PATH
```

加载：

```bash
source ~/.bashrc
```

## 4. 验证

```bash
java -version       # 应输出 1.8.0_xxx
javac -version
echo $JAVA_HOME
```

## 5. 升级流程

1. 解压新版本到 `/opt/bigdata/service/jdk1.8.0_<new>`
2. `ln -sfn jdk1.8.0_<new> jdk`
3. 重启依赖 JVM 的组件即可，**不动 `.bashrc`**
