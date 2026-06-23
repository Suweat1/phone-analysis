#!/usr/bin/env bash
# scripts/run-pipeline.sh —— Spark ETL 一键全量刷新
# 顺序：
#   1. 把本机预处理好的 parquet 推到 HDFS
#   2. （可选）InitSchemaJob：建库 + 字段字典
#       2a. --init 时先 ensure MySQL 业务库 phone_analysis 与 ads_*/ads_ext_*/ads_column_dict 16 张表
#           （文档：docs/deploy/02-mysql.md §10.1）
#       2b. spark-submit InitSchemaJob 建 Hive 4 库 + 各层表 + 写字段字典到 MySQL
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
  --init             首次部署/口径调整：先 ensure MySQL phone_analysis 库 + 表，再跑 InitSchemaJob
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
  # --files 把运行机版本的 application.properties 分发到 driver 工作目录，
  # PhoneConfig.loadConfig 会优先读它；与 start-streaming.sh 风格一致。
  # 文件不存在时退回 jar/classpath 内打包的同名文件，不报错。
  local files_opt=()
  if [ -f "${PA_REPO}/config/spark-etl/application.properties" ]; then
    files_opt=(--files "${PA_REPO}/config/spark-etl/application.properties")
  fi
  spark-submit \
    --class "$cls" \
    "${files_opt[@]}" \
    "$ETL_JAR"
  msg_ok "${cls} 完成"
}

# --------------------- 2a. ensure MySQL phone_analysis 库 + 16 张表（仅 --init） ---------------------
# 设计意图：让 --init 真正成为「首次一键」。脚本默认 MySQL 已经按 docs/deploy/02-mysql.md
#   建好 root/123456。这里只做幂等的 ensure：
#     - CREATE DATABASE IF NOT EXISTS phone_analysis
#     - 用 ddl/05-mysql-ads.sql 重新执行一遍（脚本里都是 DROP TABLE IF EXISTS + CREATE，幂等安全）
#   即使你已经按 02-mysql.md §10.1 手工执行过，这里再执行一次也只是把空表重建，不会丢数据
#   （AdsToMysqlJob 用 overwrite，每次回写都会重灌；这里也不会破坏 Hive 数据）。
ensure_mysql_app_db () {
  local sql_file="${PA_REPO}/spark-etl/src/main/resources/ddl/05-mysql-ads.sql"
  [ -f "$sql_file" ] || { msg_err "MySQL DDL 文件不存在: $sql_file"; return 1; }
  command -v mysql >/dev/null 2>&1 || { msg_err "未找到 mysql 客户端；先按 docs/deploy/02-mysql.md 部署 MySQL"; return 1; }
  port_alive "$PORT_MYSQL" || { msg_err "MySQL 未运行 (:$PORT_MYSQL)；先 bash scripts/start-all.sh --only mysql"; return 1; }

  msg_info "ensure MySQL 业务库 phone_analysis"
  mysql -uroot -p"${PASS_MYSQL_ROOT}" -e \
    "CREATE DATABASE IF NOT EXISTS phone_analysis DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" \
    2>/dev/null
  msg_info "apply ${sql_file##*/} (16 张表 DROP+CREATE，幂等)"
  mysql -uroot -p"${PASS_MYSQL_ROOT}" phone_analysis < "$sql_file" 2>/dev/null
  local cnt
  cnt=$(mysql -uroot -p"${PASS_MYSQL_ROOT}" -N -B \
        -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='phone_analysis';" \
        2>/dev/null)
  if [ "${cnt:-0}" -lt 16 ]; then
    msg_err "phone_analysis 期望至少 16 张表，实得 ${cnt}；查看 ${sql_file}"
    return 1
  fi
  msg_ok "phone_analysis 已就位 (${cnt} 张表)"
}

# --------------------- 2b. InitSchemaJob（可选） ---------------------
if [ "$INIT" -eq 1 ]; then
  ensure_mysql_app_db
  submit com.phone.etl.batch.InitSchemaJob "建 Hive 4 库 ODS/DWD/DWS/ADS + 字段字典"
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
