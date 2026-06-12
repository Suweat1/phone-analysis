#!/usr/bin/env bash
# scripts/run-pipeline.sh —— Spark ETL 一键全量刷新
# 顺序：
#   1. 把本机预处理好的 parquet 推到 HDFS
#   2. （可选）InitSchemaJob：建库 + 字段字典
#   3. PipelineJob：ods → dwd → dws → ads → mysql 一气呵成
#   4. POST /api/dict/refresh：让 SpringBoot 缓存重载字段字典
set -eo pipefail
cd "$(dirname "$0")"
. "./lib/env.sh"

INIT=0   # --init 时跑 InitSchemaJob（首次/字段口径变动）
PUSH=1   # --no-push 时跳过 hdfs put
REFRESH_APP=1   # --no-refresh-app 时不调 /api/dict/refresh
while [ $# -gt 0 ]; do
  case "$1" in
    --init) INIT=1; shift ;;
    --no-push) PUSH=0; shift ;;
    --no-refresh-app) REFRESH_APP=0; shift ;;
    -h|--help)
      cat <<EOF
用法: $0 [--init] [--no-push] [--no-refresh-app]
  --init             首次部署/口径调整：先跑 InitSchemaJob
  --no-push          跳过 hdfs put（认为 HDFS 已有最新 parquet）
  --no-refresh-app   不通知 SpringBoot 重载字段字典
EOF
      exit 0 ;;
    *) msg_err "未知参数: $1"; exit 1 ;;
  esac
done

[ -f "$ETL_JAR" ] || { msg_err "etl jar 不存在: $ETL_JAR；先跑 build-all.sh --skip-app --skip-web"; exit 1; }

# --------------------- 1. parquet → HDFS ---------------------
if [ "$PUSH" -eq 1 ]; then
  local_parquet="${PA_REPO}/data/processed/phone.parquet"
  [ -f "$local_parquet" ] || { msg_err "本地 parquet 不存在: $local_parquet (本机跑 scripts/preprocess.py 并提交 git)"; exit 1; }
  msg_info "hdfs put phone.parquet → /phone-analysis/raw/"
  hdfs dfs -mkdir -p /phone-analysis/raw
  hdfs dfs -put -f "$local_parquet" /phone-analysis/raw/
  msg_ok "parquet 推送完成"
fi

submit () {
  local cls="$1" desc="$2"
  msg_info "spark-submit ${cls}  (${desc})"
  # spark.master 在 application.properties / spark-defaults.conf 里已设为 local[*]，
  # 此处不再覆盖。如要切回 YARN，改这两个文件即可。
  spark-submit \
    --class "$cls" \
    "$ETL_JAR"
  msg_ok "${cls} 完成"
}

# --------------------- 2. InitSchemaJob（可选） ---------------------
if [ "$INIT" -eq 1 ]; then
  submit com.phone.etl.batch.InitSchemaJob "建 4 库 ODS/DWD/DWS/ADS + 字段字典"
fi

# --------------------- 3. PipelineJob ---------------------
submit com.phone.etl.batch.PipelineJob "ods→dwd→dws→ads→mysql 一键全量"

# --------------------- 4. 通知 SpringBoot ---------------------
if [ "$REFRESH_APP" -eq 1 ] && port_alive "$PORT_APP"; then
  if curl -fsS --max-time 5 -X POST "http://127.0.0.1:${PORT_APP}/api/dict/refresh" >/dev/null; then
    msg_ok "通知 app 重载字段字典"
  else
    msg_warn "app /api/dict/refresh 调用失败（app 可能没起；不影响数据）"
  fi
fi

msg_ok "Pipeline 全部完成"
