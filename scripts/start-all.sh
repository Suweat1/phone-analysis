#!/usr/bin/env bash
# scripts/start-all.sh —— 按 docs/deploy/10-startup-order.md 顺序启动所有组件
# 用法：
#   ./start-all.sh                       全启
#   ./start-all.sh --only mysql,kafka    只启部分（按依赖顺序自动重排）
#   ./start-all.sh --skip app            其余全启，不起 app
set -eo pipefail
cd "$(dirname "$0")"
. "./lib/env.sh"

ORDER=(mysql redis hdfs yarn metastore hiveserver2 zk kafka spark-history app)
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

ensure_dir "${PA_PID}"
for d in mysql redis hadoop hive kafka spark app; do ensure_dir "${PA_LOG}/${d}"; done

wait_port () {
  local port="$1" name="$2" timeout="${3:-30}"
  for _ in $(seq 1 "$timeout"); do
    port_alive "$port" && { msg_ok "$name listening :$port"; return 0; }
    sleep 1
  done
  msg_err "$name 等待端口 :$port 超时（${timeout}s）"
  return 1
}

# --------------------- 1. MySQL ---------------------
start_mysql () {
  port_alive "$PORT_MYSQL" && { msg_warn "mysql 已在 :$PORT_MYSQL"; return 0; }
  msg_info "启动 mysql..."
  nohup mysqld_safe --defaults-file=/etc/my.cnf --user=mysql \
    >"$(log_path mysql)" 2>&1 &
  echo $! > "$(pid_path mysql)"
  wait_port "$PORT_MYSQL" mysql 30
}

# --------------------- 2. Redis ---------------------
start_redis () {
  port_alive "$PORT_REDIS" && { msg_warn "redis 已在 :$PORT_REDIS"; return 0; }
  msg_info "启动 redis..."
  nohup "${REDIS_HOME}/bin/redis-server" "${REDIS_HOME}/conf/redis.conf" \
    >"$(log_path redis)" 2>&1 &
  echo $! > "$(pid_path redis)"
  wait_port "$PORT_REDIS" redis 10
}

# --------------------- 3. HDFS / 4. YARN ---------------------
start_hdfs () {
  port_alive "$PORT_NAMENODE_RPC" && { msg_warn "namenode 已在 :$PORT_NAMENODE_RPC"; return 0; }
  msg_info "start-dfs.sh..."
  "${HADOOP_HOME}/sbin/start-dfs.sh"
  wait_port "$PORT_NAMENODE_RPC" namenode 30
}
start_yarn () {
  port_alive "$PORT_YARN_RM" && { msg_warn "yarn rm 已在 :$PORT_YARN_RM"; return 0; }
  msg_info "start-yarn.sh..."
  "${HADOOP_HOME}/sbin/start-yarn.sh"
  wait_port "$PORT_YARN_RM" yarn-rm 30
}

# --------------------- 5. Hive Metastore / 6. HS2 ---------------------
start_metastore () {
  port_alive "$PORT_HIVE_METASTORE" && { msg_warn "metastore 已在 :$PORT_HIVE_METASTORE"; return 0; }
  msg_info "启动 hive metastore..."
  nohup "${HIVE_HOME}/bin/hive" --service metastore \
    >"${PA_LOG}/hive/metastore.out" 2>&1 &
  echo $! > "$(pid_path metastore)"
  wait_port "$PORT_HIVE_METASTORE" metastore 60
}
start_hiveserver2 () {
  port_alive "$PORT_HIVESERVER2" && { msg_warn "hiveserver2 已在 :$PORT_HIVESERVER2"; return 0; }
  msg_info "启动 hiveserver2..."
  nohup "${HIVE_HOME}/bin/hive" --service hiveserver2 \
    >"${PA_LOG}/hive/hiveserver2.out" 2>&1 &
  echo $! > "$(pid_path hiveserver2)"
  wait_port "$PORT_HIVESERVER2" hiveserver2 60
}

# --------------------- 7. Zookeeper / 8. Kafka ---------------------
start_zk () {
  port_alive "$PORT_ZOOKEEPER" && { msg_warn "zookeeper 已在 :$PORT_ZOOKEEPER"; return 0; }
  msg_info "启动 zookeeper..."
  nohup "${KAFKA_HOME}/bin/zookeeper-server-start.sh" \
        "${KAFKA_HOME}/config/zookeeper.properties" \
    >"${PA_LOG}/kafka/zk.out" 2>&1 &
  echo $! > "$(pid_path zookeeper)"
  wait_port "$PORT_ZOOKEEPER" zookeeper 20
}
start_kafka () {
  port_alive "$PORT_KAFKA" && { msg_warn "kafka 已在 :$PORT_KAFKA"; return 0; }
  msg_info "启动 kafka..."
  nohup "${KAFKA_HOME}/bin/kafka-server-start.sh" \
        "${KAFKA_HOME}/config/server.properties" \
    >"${PA_LOG}/kafka/kafka.out" 2>&1 &
  echo $! > "$(pid_path kafka)"
  wait_port "$PORT_KAFKA" kafka 30
}

# --------------------- 9. Spark History ---------------------
start_spark_history () {
  port_alive "$PORT_SPARK_HISTORY" && { msg_warn "spark-history 已在 :$PORT_SPARK_HISTORY"; return 0; }
  msg_info "start-history-server.sh..."
  "${SPARK_HOME}/sbin/start-history-server.sh"
  wait_port "$PORT_SPARK_HISTORY" spark-history 15
}

# --------------------- 10. Spring Boot ----------------------
start_app () {
  port_alive "$PORT_APP" && { msg_warn "app 已在 :$PORT_APP"; return 0; }
  [ -f "$APP_JAR" ] || { msg_err "app jar 不存在: $APP_JAR；先跑 build-all.sh"; return 1; }
  msg_info "启动 SpringBoot..."
  # application.yml 已在 jar/classpath 内（app/src/main/resources/application.yml）。
  # 如需运行机覆盖，加：--spring.config.additional-location=file:${PA_REPO}/config/app/application.yml
  nohup java -jar "$APP_JAR" \
    >"$(log_path app)" 2>&1 &
  echo $! > "$(pid_path app)"
  wait_port "$PORT_APP" app 60
}

# --------------------- 调度 ---------------------
for c in "${ORDER[@]}"; do
  want "$c" || { msg_info "skip $c"; continue; }
  case "$c" in
    mysql)        start_mysql ;;
    redis)        start_redis ;;
    hdfs)         start_hdfs ;;
    yarn)         start_yarn ;;
    metastore)    start_metastore ;;
    hiveserver2)  start_hiveserver2 ;;
    zk)           start_zk ;;
    kafka)        start_kafka ;;
    spark-history) start_spark_history ;;
    app)          start_app ;;
  esac
done

msg_ok "启动完成。jps 查看进程：jps -l"
