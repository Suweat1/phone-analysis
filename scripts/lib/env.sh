#!/usr/bin/env bash
# scripts/lib/env.sh —— phone-analysis 一键脚本的全局变量
# 由所有 start/stop/deploy/build 脚本统一 source；唯一真实源
# 修改任何端口/路径/密码：只动本文件，业务代码与启停脚本无需变更

# ─── 仓库与运行机基础路径 ──────────────────────────────────────────
export PA_USER="${PA_USER:-bigdata}"
export PA_HOST="${PA_HOST:-phone-analysis}"
export PA_REPO="${PA_REPO:-${HOME}/phone-analysis}"          # git clone 目录
export PA_BASE="${PA_BASE:-/opt/bigdata}"
export PA_LOG="${PA_BASE}/log"
export PA_DATA="${PA_BASE}/data"
export PA_SOFTWARE="${PA_BASE}/software"
export PA_SERVICE="${PA_BASE}/service"
export PA_PID="${PA_DATA}/pid"
export PA_JARS="${PA_DATA}/jars"

# ─── 组件家目录（与 docs/deploy/ 一致） ───────────────────────────
export JAVA_HOME="${JAVA_HOME:-${PA_SERVICE}/jdk}"
export HADOOP_HOME="${HADOOP_HOME:-${PA_SERVICE}/hadoop}"
export HIVE_HOME="${HIVE_HOME:-${PA_SERVICE}/hive}"
export KAFKA_HOME="${KAFKA_HOME:-${PA_SERVICE}/kafka}"
export SPARK_HOME="${SPARK_HOME:-${PA_SERVICE}/spark}"
export MAVEN_HOME="${MAVEN_HOME:-${PA_SERVICE}/maven}"
export REDIS_HOME="${REDIS_HOME:-${PA_SERVICE}/redis}"

export PATH="${JAVA_HOME}/bin:${HADOOP_HOME}/bin:${HADOOP_HOME}/sbin:\
${HIVE_HOME}/bin:${KAFKA_HOME}/bin:${SPARK_HOME}/bin:${SPARK_HOME}/sbin:\
${MAVEN_HOME}/bin:${REDIS_HOME}/bin:${PATH}"

# ─── 端口（与 config/* 完全对齐） ─────────────────────────────────
export PORT_MYSQL=3306
export PORT_REDIS=6379
export PORT_NAMENODE_RPC=9000
export PORT_NAMENODE_HTTP=9870
export PORT_YARN_RM=8088
export PORT_MAPRED_HISTORY=19888
export PORT_HIVE_METASTORE=9083
export PORT_HIVESERVER2=10000
export PORT_ZOOKEEPER=2181
export PORT_KAFKA=9092
export PORT_SPARK_HISTORY=18080
export PORT_APP=8080

# ─── 密码（与 CLAUDE.md 锁定值一致） ──────────────────────────────
export PASS_MYSQL_ROOT="${PASS_MYSQL_ROOT:-123456}"
export PASS_HIVE="${PASS_HIVE:-123456}"
export PASS_REDIS="${PASS_REDIS:-redis123}"

# ─── jar 包与构建产物名 ───────────────────────────────────────────
export APP_JAR_NAME="phone-analysis-app.jar"
export ETL_JAR_NAME="phone-analysis-spark-etl.jar"
export APP_JAR="${PA_JARS}/${APP_JAR_NAME}"
export ETL_JAR="${PA_JARS}/${ETL_JAR_NAME}"

# ─── 日志路径模板 ─────────────────────────────────────────────────
log_path () { echo "${PA_LOG}/$1/$1.out"; }
pid_path () { echo "${PA_PID}/$1.pid"; }

# ─── 终端着色（带 tty 才生效） ────────────────────────────────────
if [ -t 1 ]; then
  C_OK='\033[1;32m'; C_WARN='\033[1;33m'; C_ERR='\033[1;31m'; C_DIM='\033[2m'; C_END='\033[0m'
else
  C_OK=''; C_WARN=''; C_ERR=''; C_DIM=''; C_END=''
fi
msg_ok ()   { printf "${C_OK}[ OK ]${C_END} %s\n"   "$*"; }
msg_warn () { printf "${C_WARN}[WARN]${C_END} %s\n" "$*"; }
msg_err ()  { printf "${C_ERR}[FAIL]${C_END} %s\n"  "$*" >&2; }
msg_info () { printf "${C_DIM}[INFO]${C_END} %s\n"  "$*"; }

# ─── 通用助手 ─────────────────────────────────────────────────────
ensure_dir () {
  for d in "$@"; do mkdir -p "$d"; done
}

# 通过 pid 文件停止进程；不存在或失效时用 pattern 回落
stop_by_pid_or_pattern () {
  local name="$1" pattern="$2"
  local pid_file
  pid_file="$(pid_path "$name")"
  if [ -f "$pid_file" ] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
    kill "$(cat "$pid_file")" && msg_ok "$name stopped (pid $(cat "$pid_file"))"
    rm -f "$pid_file"
  elif pgrep -f "$pattern" >/dev/null 2>&1; then
    pkill -f "$pattern" && msg_ok "$name stopped (by pattern)"
    rm -f "$pid_file" 2>/dev/null || true
  else
    msg_warn "$name not running"
  fi
}

# 端口探测（基于 ss，未装则用 nc）
port_alive () {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    ss -ltn "( sport = :${port} )" | grep -q ":${port}"
  else
    nc -z 127.0.0.1 "${port}" 2>/dev/null
  fi
}
