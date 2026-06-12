#!/usr/bin/env bash
# scripts/reset-hive-metastore.sh
# ─────────────────────────────────────────────────────────────────────
# 重建 MySQL 上的 Hive metastore 元数据库，规避 Hive 3.1.3 启动时
# 自动 ALTER TABLE KEY_CONSTRAINTS ADD CONSTRAINT KEY_CONSTRAINTS_FK4
# 在大表 + 无索引 + 残留 hive 连接抢锁场景下的死锁。
#
# 破局思路（与 docs/deploy/05-hive.md §6.1 同源）：
#   1. 停 hiveserver2 + metastore   —— 切断所有 hive 用户连接
#   2. 确认 MySQL 在跑              —— 不在跑就只能手工先 start-all.sh --only mysql
#   3. KILL processlist 里残留的 hive 连接（兜底）
#   4. DROP DATABASE hive_metastore；CREATE DATABASE hive_metastore
#      重新授权 hive 账号
#   5. schematool -dbType mysql -initSchema   —— 重建 70+ 张表
#   6. ★ 立刻在 KEY_CONSTRAINTS 上加两个索引（此刻 metastore 还没起，
#        没有任何 hive 连接抢锁，秒过）
#   7. 起 metastore + HS2
#
# 危险等级：会清空 Hive 全部元数据（库/表/分区定义全没）。
#          HDFS 上的实际数据文件（/phone-analysis/raw 等）不动。
#          重建后 Hive 库表得重新跑 InitSchemaJob 或 ddl/*.sql。
#
# 用法：
#   bash scripts/reset-hive-metastore.sh --yes
#   bash scripts/reset-hive-metastore.sh --yes --skip-restart   # 重建后不自动起 metastore/HS2
# ─────────────────────────────────────────────────────────────────────

set -eo pipefail
cd "$(dirname "$0")"
. "./lib/env.sh"

CONFIRM=""
SKIP_RESTART=""
while [ $# -gt 0 ]; do
  case "$1" in
    --yes|-y)       CONFIRM=1; shift ;;
    --skip-restart) SKIP_RESTART=1; shift ;;
    -h|--help)
      sed -n '2,30p' "$0"
      exit 0 ;;
    *) msg_err "未知参数: $1"; exit 1 ;;
  esac
done

if [ -z "$CONFIRM" ]; then
  cat <<EOF
${C_WARN}即将 DROP DATABASE hive_metastore 并重建。这会清掉 Hive 所有库/表/分区
元数据（HDFS 数据文件保留）。重建后需要重新跑：
  bash scripts/run-pipeline.sh --init
确认请加 --yes 重新执行。${C_END}
EOF
  exit 1
fi

MYSQL_PWD_OPT=(-uroot "-p${PASS_MYSQL_ROOT}")
mysql_q () { mysql "${MYSQL_PWD_OPT[@]}" -N -B -e "$1"; }

# ─── 1. 停 hiveserver2 + metastore ───────────────────────────────────
msg_info "(1/7) 停 hiveserver2 + metastore"
stop_by_pid_or_pattern hiveserver2 'HiveServer2'   || true
stop_by_pid_or_pattern metastore   'HiveMetaStore' || true
# 兜底：jps 还能看到 RunJar 就再补一刀
if pgrep -f 'Dproc_metastore' >/dev/null 2>&1; then
  msg_warn "metastore 残留，pkill -9 收尾"
  pkill -9 -f 'Dproc_metastore' || true
fi
if pgrep -f 'Dproc_hiveserver2' >/dev/null 2>&1; then
  msg_warn "hiveserver2 残留，pkill -9 收尾"
  pkill -9 -f 'Dproc_hiveserver2' || true
fi
sleep 2

# ─── 2. 确认 MySQL 可连（root 不带 db，避免 USE 卡） ─────────────────
msg_info "(2/7) 检查 MySQL 状态"
if ! port_alive "$PORT_MYSQL"; then
  msg_err "MySQL 未运行；请先：bash scripts/start-all.sh --only mysql"
  exit 1
fi
if ! mysql_q "SELECT 1" >/dev/null 2>&1; then
  msg_err "MySQL 在跑但 root 连不上（密码 PASS_MYSQL_ROOT=${PASS_MYSQL_ROOT} 是否正确？）"
  exit 1
fi
msg_ok "MySQL OK"

# ─── 3. KILL 残留的 hive 连接 ────────────────────────────────────────
msg_info "(3/7) 清理 MySQL 中残留的 hive 用户连接"
HIVE_CONN_KILL_SQL=$(mysql_q "
  SELECT IFNULL(GROUP_CONCAT(CONCAT('KILL ', id) SEPARATOR ';'), '')
  FROM information_schema.processlist
  WHERE user = 'hive';
")
if [ -n "$HIVE_CONN_KILL_SQL" ]; then
  msg_warn "发现残留 hive 连接，执行 KILL"
  mysql "${MYSQL_PWD_OPT[@]}" -e "${HIVE_CONN_KILL_SQL};" 2>/dev/null || true
  sleep 1
fi
LEFT=$(mysql_q "SELECT COUNT(*) FROM information_schema.processlist WHERE user='hive';")
if [ "$LEFT" -gt 0 ]; then
  msg_warn "仍有 ${LEFT} 个 hive 连接（可能正在新建中），继续"
else
  msg_ok "无残留 hive 连接"
fi

# ─── 4. DROP + CREATE hive_metastore + 授权 ──────────────────────────
msg_info "(4/7) DROP & CREATE hive_metastore + GRANT"
mysql "${MYSQL_PWD_OPT[@]}" <<SQL
DROP DATABASE IF EXISTS hive_metastore;
CREATE DATABASE hive_metastore DEFAULT CHARACTER SET utf8 COLLATE utf8_general_ci;
-- 与 docs/deploy/02-mysql.md §9 一致；账号若已存在则跳过
CREATE USER IF NOT EXISTS 'hive'@'%' IDENTIFIED WITH mysql_native_password BY '${PASS_HIVE}';
GRANT ALL PRIVILEGES ON hive_metastore.* TO 'hive'@'%';
FLUSH PRIVILEGES;
SQL
msg_ok "hive_metastore 已重建"

# ─── 5. schematool initSchema ────────────────────────────────────────
msg_info "(5/7) schematool -dbType mysql -initSchema"
if [ ! -x "${HIVE_HOME}/bin/schematool" ]; then
  msg_err "schematool 不存在: ${HIVE_HOME}/bin/schematool"
  exit 1
fi
"${HIVE_HOME}/bin/schematool" -dbType mysql -initSchema \
  >"${PA_LOG}/hive/schematool-init.out" 2>&1 || {
    msg_err "schematool 失败，详情：${PA_LOG}/hive/schematool-init.out"
    tail -n 40 "${PA_LOG}/hive/schematool-init.out" >&2 || true
    exit 1
  }
msg_ok "schematool init OK（日志：${PA_LOG}/hive/schematool-init.out）"

# ─── 6. ★ 关键：在 metastore 起来前加 KEY_CONSTRAINTS 索引 ──────────
msg_info "(6/7) 给 KEY_CONSTRAINTS 加索引（此刻无 hive 连接抢锁，秒过）"
mysql "${MYSQL_PWD_OPT[@]}" hive_metastore <<'SQL'
ALTER TABLE KEY_CONSTRAINTS ADD INDEX CONSTRAINTS_PARENT_TBL_ID_INDEX (PARENT_TBL_ID);
ALTER TABLE KEY_CONSTRAINTS ADD INDEX CONSTRAINTS_CHILD_TBL_ID_INDEX  (CHILD_TBL_ID);
SQL
INDEX_CNT=$(mysql_q "
  SELECT COUNT(DISTINCT INDEX_NAME)
  FROM information_schema.statistics
  WHERE table_schema='hive_metastore' AND table_name='KEY_CONSTRAINTS'
    AND INDEX_NAME IN ('CONSTRAINTS_PARENT_TBL_ID_INDEX','CONSTRAINTS_CHILD_TBL_ID_INDEX');
")
if [ "$INDEX_CNT" -ne 2 ]; then
  msg_err "索引数量不对，期望 2 实得 ${INDEX_CNT}"
  exit 1
fi
msg_ok "KEY_CONSTRAINTS 两个索引已就位"

# ─── 7. 起 metastore + HS2 ───────────────────────────────────────────
if [ -n "$SKIP_RESTART" ]; then
  msg_warn "(7/7) --skip-restart：不自动起 metastore/HS2"
  msg_info "下一步：bash scripts/start-all.sh --only metastore,hiveserver2"
  msg_info "        bash scripts/run-pipeline.sh --init"
  exit 0
fi

msg_info "(7/7) 启动 metastore + hiveserver2"
bash "$(dirname "$0")/start-all.sh" --only metastore,hiveserver2

cat <<EOF

${C_OK}reset 完成。${C_END}下一步：
  bash scripts/run-pipeline.sh --init      # 重建 phone_ods/dwd/dws/ads 4 库 + 跑 ETL

如果再卡（说明 InnoDB 还残留行锁），按 docs/deploy/05-hive.md §6.1 末尾「重启
MySQL 后再跑本脚本」处理。
EOF
