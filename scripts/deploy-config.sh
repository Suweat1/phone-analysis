#!/usr/bin/env bash
# scripts/deploy-config.sh —— git pull 后把 config/<组件>/* 部署到各组件实际路径
# 设计原则：
#  - **首选 symlink**：配置只在仓库内一份，组件目录指向仓库；改完 git push 后 git pull 即生效。
#  - 部分文件 OS 要求绝对路径（如 /etc/my.cnf），用 cp 并备份原文件。
#  - 不修改任何业务代码；不修改组件二进制目录的内容。
set -eo pipefail
cd "$(dirname "$0")"
. "./lib/env.sh"

REPO_CONF="${PA_REPO}/config"
[ -d "$REPO_CONF" ] || { msg_err "仓库配置目录不存在: $REPO_CONF"; exit 1; }

msg_info "目标主机: ${PA_HOST}    用户: ${PA_USER}    仓库: ${PA_REPO}"
ensure_dir "${PA_LOG}/{mysql,redis,hadoop,hive,kafka,spark,app}" "${PA_PID}" "${PA_JARS}"
# 上面 brace 在 ensure_dir 里不会展开，单独再做一次
for c in mysql redis hadoop hive kafka spark app; do
  ensure_dir "${PA_LOG}/${c}"
done

# 通用 link 函数：把 仓库文件 软链 到 组件目录
link () {
  local src="$1" dst="$2"
  if [ ! -e "$src" ]; then msg_warn "skip (源不存在): $src"; return; fi
  if [ -L "$dst" ] && [ "$(readlink "$dst")" = "$src" ]; then
    msg_info "ok   $dst -> $src"
    return
  fi
  # 第一次部署：原文件 / 模板备份为 .bak
  if [ -e "$dst" ] && [ ! -L "$dst" ]; then
    mv "$dst" "${dst}.bak.$(date +%s)" || true
  fi
  ln -sfn "$src" "$dst"
  msg_ok "link $dst -> $src"
}

# --------------------- 1. MySQL ---------------------
# my.cnf 系统要求路径固定 → cp（mysqld 不读符号链接的某些发行版会报错）
if [ -f "${REPO_CONF}/mysql/my.cnf" ]; then
  if [ "$(id -u)" -eq 0 ]; then
    [ -f /etc/my.cnf ] && [ ! -L /etc/my.cnf ] && cp /etc/my.cnf "/etc/my.cnf.bak.$(date +%s)" || true
    cp "${REPO_CONF}/mysql/my.cnf" /etc/my.cnf
    msg_ok "cp   /etc/my.cnf"
  else
    sudo cp "${REPO_CONF}/mysql/my.cnf" /etc/my.cnf && msg_ok "sudo cp /etc/my.cnf"
  fi
fi

# --------------------- 2. Redis ---------------------
ensure_dir "${REDIS_HOME}/conf"
link "${REPO_CONF}/redis/redis.conf" "${REDIS_HOME}/conf/redis.conf"

# --------------------- 3. Hadoop ---------------------
for f in hadoop-env.sh core-site.xml hdfs-site.xml mapred-site.xml yarn-site.xml workers; do
  link "${REPO_CONF}/hadoop/${f}" "${HADOOP_HOME}/etc/hadoop/${f}"
done

# --------------------- 4. Hive ---------------------
for f in hive-env.sh hive-site.xml; do
  link "${REPO_CONF}/hive/${f}" "${HIVE_HOME}/conf/${f}"
done
# Spark 必须看到 hive-site.xml 才能用 Hive Metastore
link "${REPO_CONF}/hive/hive-site.xml" "${SPARK_HOME}/conf/hive-site.xml"

# --------------------- 5. Kafka ---------------------
for f in zookeeper.properties server.properties; do
  link "${REPO_CONF}/kafka/${f}" "${KAFKA_HOME}/config/${f}"
done

# --------------------- 6. Spark ---------------------
for f in spark-env.sh spark-defaults.conf; do
  link "${REPO_CONF}/spark/${f}" "${SPARK_HOME}/conf/${f}"
done

# --------------------- 7. Maven ---------------------
ensure_dir "${HOME}/.m2"
link "${REPO_CONF}/maven/settings.xml" "${HOME}/.m2/settings.xml"

# --------------------- 8. app / spark-etl ----------------------
# Spring Boot 启动用 --spring.config.location=file:${PA_REPO}/config/app/application.yml
# Spark ETL 启动用 --files ${PA_REPO}/config/spark-etl/application.properties
# 不复制，只校验
[ -f "${REPO_CONF}/app/application.yml" ]              && msg_ok "app/application.yml          present"
[ -f "${REPO_CONF}/spark-etl/application.properties" ] && msg_ok "spark-etl/application.properties present"

msg_ok "配置部署完成"
