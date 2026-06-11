# 03 - Redis 6.2.7（源码编译）

> Redis 官方 **不提供** Linux 预编译二进制包，必须从源码 `make`。

## 1. 系统依赖

已在 [00-system-prepare.md](./00-system-prepare.md) 安装：

```
build-essential tcl pkg-config
```

## 2. 编译

```bash
cd /opt/bigdata/software/
tar -xzf redis-6.2.7.tar.gz
cd redis-6.2.7
make -j$(nproc)
# 可选：make test     （需要 tcl）
sudo make PREFIX=/opt/bigdata/service/redis install
```

`make install` 后 `/opt/bigdata/service/redis/bin/` 下会出现：

```
redis-server  redis-cli  redis-benchmark  redis-sentinel  redis-check-aof  redis-check-rdb
```

## 3. 目录

```bash
mkdir -p /opt/bigdata/service/redis/conf
mkdir -p /opt/bigdata/data/redis
mkdir -p /opt/bigdata/log/redis
chown -R bigdata:bigdata /opt/bigdata/service/redis /opt/bigdata/data/redis /opt/bigdata/log/redis
```

## 4. 配置文件

**项目版本**：`config/redis/redis.conf`

**部署**：

```bash
cp ~/phone-analysis/config/redis/redis.conf /opt/bigdata/service/redis/conf/redis.conf
```

`redis.conf` 关键参数：

```conf
bind 0.0.0.0
protected-mode yes
port 6379
daemonize yes
pidfile /opt/bigdata/service/redis/redis.pid
logfile /opt/bigdata/log/redis/redis.log
dir /opt/bigdata/data/redis
requirepass redis123
appendonly yes
appendfsync everysec
maxmemory 1gb
maxmemory-policy allkeys-lru
```

> `daemonize yes` 已经让 redis 自身后台运行，无需再用 nohup；**不要** 启用任何 systemd unit。

## 5. 环境变量（`~/.bashrc`）

```bash
# Redis
export REDIS_HOME=/opt/bigdata/service/redis
export PATH=$REDIS_HOME/bin:$PATH
```

## 6. 启停

```bash
# 启动
redis-server /opt/bigdata/service/redis/conf/redis.conf

# 停止
redis-cli -h 127.0.0.1 -p 6379 -a redis123 shutdown
```

## 验证

```bash
redis-cli -a redis123 ping       # 返回 PONG
redis-cli -a redis123 info server | grep redis_version
```
