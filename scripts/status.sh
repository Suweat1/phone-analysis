#!/usr/bin/env bash
# scripts/status.sh —— 一行确认整套是否健康：jps 进程 + 关键端口 + REST 健康
set -eo pipefail
cd "$(dirname "$0")"
. "./lib/env.sh"

check_port () {
  local port="$1" name="$2"
  if port_alive "$port"; then printf "  %-22s :%-5s ${C_OK}OK${C_END}\n" "$name" "$port"
  else                        printf "  %-22s :%-5s ${C_ERR}DOWN${C_END}\n" "$name" "$port"
  fi
}

echo "── 端口 ──────────────────────────────────"
check_port "$PORT_MYSQL"          mysql
check_port "$PORT_REDIS"          redis
check_port "$PORT_NAMENODE_RPC"   namenode-rpc
check_port "$PORT_NAMENODE_HTTP"  namenode-web
check_port "$PORT_YARN_RM"        yarn-rm
check_port "$PORT_HIVE_METASTORE" hive-metastore
check_port "$PORT_HIVESERVER2"    hiveserver2
check_port "$PORT_ZOOKEEPER"      zookeeper
check_port "$PORT_KAFKA"          kafka
check_port "$PORT_SPARK_HISTORY"  spark-history
check_port "$PORT_APP"            springboot-app

echo
echo "── jps 进程 ──────────────────────────────"
if command -v jps >/dev/null 2>&1; then
  jps -l | grep -Ev "Jps$" | sort -k2
else
  msg_warn "jps 未安装（${JAVA_HOME}/bin/jps）"
fi

echo
echo "── REST 健康 ─────────────────────────────"
if port_alive "$PORT_APP"; then
  rsp=$(curl -fsS --max-time 3 "http://127.0.0.1:${PORT_APP}/api/health" 2>/dev/null || echo "")
  if [ -n "$rsp" ]; then
    echo "  /api/health => $rsp"
  else
    msg_warn "/api/health 无响应（app 起来但还没就绪？看 ${PA_LOG}/app/app.out）"
  fi
else
  msg_warn "app 未运行，跳过 REST"
fi
