#!/usr/bin/env bash
# scripts/build-all.sh —— 运行机上构建所有模块的产物
#   1. mvn package：app + spark-etl
#   2. npm run build：web → dist/
#   3. 把两个 jar copy 到 ${PA_JARS}/，前端 dist/ 留在原地由 nginx/Spring 静态映射
# 选项:
#   --skip-app | --skip-etl | --skip-web    跳过对应模块
#   --skip-tests （默认开启）  附加 -DskipTests
set -eo pipefail
cd "$(dirname "$0")"
. "./lib/env.sh"

SKIP_APP=0; SKIP_ETL=0; SKIP_WEB=0
MVN_OPTS="-DskipTests"
for a in "$@"; do
  case "$a" in
    --skip-app) SKIP_APP=1 ;;
    --skip-etl) SKIP_ETL=1 ;;
    --skip-web) SKIP_WEB=1 ;;
    --no-skip-tests) MVN_OPTS="" ;;
    -h|--help)
      cat <<EOF
用法: $0 [--skip-app] [--skip-etl] [--skip-web] [--no-skip-tests]
EOF
      exit 0 ;;
    *) msg_err "未知参数: $a"; exit 1 ;;
  esac
done

ensure_dir "${PA_JARS}"
cd "${PA_REPO}"

# --------------------- 1. app + spark-etl ---------------------
if [ "$SKIP_APP" -eq 0 ] || [ "$SKIP_ETL" -eq 0 ]; then
  command -v mvn >/dev/null 2>&1 || { msg_err "未找到 mvn，请确认 ${MAVEN_HOME}/bin 在 PATH 中"; exit 1; }
  modules=()
  [ "$SKIP_APP" -eq 0 ] && modules+=("app")
  [ "$SKIP_ETL" -eq 0 ] && modules+=("spark-etl")
  IFS=','; mod_csv="${modules[*]}"; unset IFS
  msg_info "mvn package -pl ${mod_csv} -am ${MVN_OPTS}"
  mvn ${MVN_OPTS} -pl "${mod_csv}" -am clean package
fi

if [ "$SKIP_APP" -eq 0 ]; then
  src=$(ls "${PA_REPO}/app/target/"*.jar 2>/dev/null | grep -v "original-" | head -1)
  [ -n "$src" ] || { msg_err "未找到 app jar 产物"; exit 1; }
  cp "$src" "${APP_JAR}"
  msg_ok "app jar -> ${APP_JAR}"
fi
if [ "$SKIP_ETL" -eq 0 ]; then
  src=$(ls "${PA_REPO}/spark-etl/target/"*.jar 2>/dev/null | grep -v "original-" | head -1)
  [ -n "$src" ] || { msg_err "未找到 spark-etl jar 产物"; exit 1; }
  cp "$src" "${ETL_JAR}"
  msg_ok "etl jar -> ${ETL_JAR}"
fi

# --------------------- 2. web --------------------------------
if [ "$SKIP_WEB" -eq 0 ]; then
  command -v npm >/dev/null 2>&1 || { msg_err "未找到 npm"; exit 1; }
  cd "${PA_REPO}/web"
  # 把 config/web/.env* 链接到 web/ 里 vue cli 才认
  for f in .env .env.development .env.production; do
    [ -f "${PA_REPO}/config/web/${f}" ] && ln -sfn "../config/web/${f}" "./${f}"
  done
  msg_info "npm ci && npm run build"
  [ -d node_modules ] || npm ci
  npm run build
  msg_ok "web 产物 -> ${PA_REPO}/web/dist/"
fi

msg_ok "构建完成"
