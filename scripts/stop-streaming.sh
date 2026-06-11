#!/usr/bin/env bash
# scripts/stop-streaming.sh —— 优雅停止 Spark Structured Streaming 进程
# 借助 spark.streaming.stopGracefullyOnShutdown=true：SIGTERM 后会完成当前 batch 再退出
set -eo pipefail
cd "$(dirname "$0")"
. "./lib/env.sh"

stop_by_pid_or_pattern streaming "com\.phone\.etl\.streaming\.RawStreamingJob"
