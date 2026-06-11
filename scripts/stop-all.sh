#!/usr/bin/env bash
# scripts/stop-all.sh —— 反向停止所有组件
# 优先用 ${PA_PID}/<name>.pid，pid 失效用 pkill -f 兜底
set -eo pipefail
cd "$(dirname "$0")"
. "./lib/env.sh"

ORDER=(app spark-history kafka zk hiveserver2 metastore yarn hdfs redis mysql)
ONLY=""; SKIP=""
while [ $# -gt 0 ]; do
  case "$1" in
    --only) ONLY="$2"; shift 2 ;;
    --skip) SKIP="$2"; shift 2 ;;
    -h|--help)
      cat <<EOF
用法: $0 [--only c1,c2,...] [--skip c1,c2,...]
组件名: ${ORDER[*]}
EOF
      exit 0 ;;
    *) msg_err "未知参数: $1"; exit 1 ;;
  esac
done
in_csv () { case ",$1," in *",$2,"*) return 0 ;; esac; return 1; }
want () {
  local c="$1"
  if [ -n "$ONLY" ]; then in_csv "$ONLY" "$c" && return 0 || return 1; fi
  if [ -n "$SKIP" ] && in_csv "$SKIP" "$c"; then return 1; fi
  return 0
}

stop_app () { stop_by_pid_or_pattern app "${APP_JAR_NAME}"; }
stop_spark_history () {
  "${SPARK_HOME}/sbin/stop-history-server.sh" >/dev/null 2>&1 \
    && msg_ok "spark-history stopped" || msg_warn "spark-history not running"
}
stop_kafka () {
  if pgrep -f 'kafka\.Kafka' >/dev/null 2>&1; then
    "${KAFKA_HOME}/bin/kafka-server-stop.sh" >/dev/null 2>&1 || true
    msg_ok "kafka stopped"
  else msg_warn "kafka not running"; fi
  rm -f "$(pid_path kafka)" 2>/dev/null || true
}
stop_zk () {
  if pgrep -f QuorumPeerMain >/dev/null 2>&1; then
    "${KAFKA_HOME}/bin/zookeeper-server-stop.sh" >/dev/null 2>&1 || true
    msg_ok "zookeeper stopped"
  else msg_warn "zookeeper not running"; fi
  rm -f "$(pid_path zookeeper)" 2>/dev/null || true
}
stop_hiveserver2 () { stop_by_pid_or_pattern hiveserver2 'HiveServer2'; }
stop_metastore ()   { stop_by_pid_or_pattern metastore   'HiveMetaStore'; }
stop_yarn () {
  if port_alive "$PORT_YARN_RM"; then
    "${HADOOP_HOME}/sbin/stop-yarn.sh" >/dev/null 2>&1 && msg_ok "yarn stopped"
  else msg_warn "yarn not running"; fi
}
stop_hdfs () {
  if port_alive "$PORT_NAMENODE_RPC"; then
    "${HADOOP_HOME}/sbin/stop-dfs.sh" >/dev/null 2>&1 && msg_ok "hdfs stopped"
  else msg_warn "hdfs not running"; fi
}
stop_redis () {
  if port_alive "$PORT_REDIS"; then
    "${REDIS_HOME}/bin/redis-cli" -a "${PASS_REDIS}" shutdown 2>/dev/null \
      && msg_ok "redis stopped"
  else msg_warn "redis not running"; fi
  rm -f "$(pid_path redis)" 2>/dev/null || true
}
stop_mysql () {
  if port_alive "$PORT_MYSQL"; then
    mysqladmin -uroot -p"${PASS_MYSQL_ROOT}" shutdown 2>/dev/null \
      && msg_ok "mysql stopped" \
      || { msg_warn "mysqladmin shutdown 失败，回落 pkill"; pkill -f mysqld_safe 2>/dev/null || true; pkill -f bin/mysqld 2>/dev/null || true; }
  else msg_warn "mysql not running"; fi
  rm -f "$(pid_path mysql)" 2>/dev/null || true
}

for c in "${ORDER[@]}"; do
  want "$c" || { msg_info "skip $c"; continue; }
  case "$c" in
    app)           stop_app ;;
    spark-history) stop_spark_history ;;
    kafka)         stop_kafka ;;
    zk)            stop_zk ;;
    hiveserver2)   stop_hiveserver2 ;;
    metastore)     stop_metastore ;;
    yarn)          stop_yarn ;;
    hdfs)          stop_hdfs ;;
    redis)         stop_redis ;;
    mysql)         stop_mysql ;;
  esac
done

msg_ok "停止完成"
