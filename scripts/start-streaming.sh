#!/usr/bin/env bash
# scripts/start-streaming.sh —— 启动 Spark Structured Streaming 长进程
# 与 PipelineJob 不同，本进程不会自然退出，需要单独的 pid 管理
set -eo pipefail
cd "$(dirname "$0")"
. "./lib/env.sh"

[ -f "$ETL_JAR" ] || { msg_err "etl jar 不存在: $ETL_JAR；先跑 build-all.sh --skip-app --skip-web"; exit 1; }

PID_FILE="$(pid_path streaming)"
LOG_FILE="${PA_LOG}/spark/streaming.out"
ensure_dir "${PA_LOG}/spark"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  msg_warn "streaming 已在运行 (pid $(cat "$PID_FILE")); 如需重启先 stop-streaming.sh"
  exit 0
fi

msg_info "spark-submit RawStreamingJob → ${LOG_FILE}"
nohup spark-submit \
  --master yarn --deploy-mode client \
  --class com.phone.etl.streaming.RawStreamingJob \
  --conf spark.streaming.stopGracefullyOnShutdown=true \
  --files "${PA_REPO}/config/spark-etl/application.properties" \
  "$ETL_JAR" \
  >"$LOG_FILE" 2>&1 &

echo $! > "$PID_FILE"
sleep 2
if kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  msg_ok "streaming 启动 (pid $(cat "$PID_FILE"))"
  msg_info "首批数据需要 15~30s 才会出现告警；tail -f $LOG_FILE 观察"
else
  msg_err "streaming 启动失败，看日志: $LOG_FILE"
  rm -f "$PID_FILE"
  exit 1
fi
