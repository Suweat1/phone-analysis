#!/usr/bin/env bash
# scripts/build-all.sh —— 运行机上构建所有模块的产物
#   1. npm run build：web → web/dist/
#   2. cp web/dist/* → app/src/main/resources/static/   （让 SpringBoot 打 fat-jar 时静态资源进 classpath）
#   3. mvn package：app + spark-etl
#   4. 把两个 jar copy 到 ${PA_JARS}/
# 选项:
#   --skip-app | --skip-etl | --skip-web    跳过对应模块
#   --skip-static    web 构建后不拷贝 dist 到 app/static（迭代调试 / 单独看后端时用）
#   --skip-tests （默认开启）  附加 -DskipTests
set -eo pipefail
cd "$(dirname "$0")"
. "./lib/env.sh"

SKIP_APP=0; SKIP_ETL=0; SKIP_WEB=0; SKIP_STATIC=0
MVN_OPTS="-DskipTests"
for a in "$@"; do
  case "$a" in
    --skip-app) SKIP_APP=1 ;;
    --skip-etl) SKIP_ETL=1 ;;
    --skip-web) SKIP_WEB=1 ;;
    --skip-static) SKIP_STATIC=1 ;;
    --no-skip-tests) MVN_OPTS="" ;;
    -h|--help)
      cat <<EOF
用法: $0 [--skip-app] [--skip-etl] [--skip-web] [--skip-static] [--no-skip-tests]

构建顺序（重要）：web → cp dist → mvn package
  web 必须先于 mvn，否则 fat-jar 里没有 static/* 静态资源。
  --skip-web 隐式开启 --skip-static（无新 dist 可拷）。
EOF
      exit 0 ;;
    *) msg_err "未知参数: $a"; exit 1 ;;
  esac
done

ensure_dir "${PA_JARS}"
cd "${PA_REPO}"

# --------------------- 1. web --------------------------------
# 必须在 mvn 之前，否则 spring-boot fat-jar 抓不到 dist/ 里的产物
WEB_DIST="${PA_REPO}/web/dist"
STATIC_DIR="${PA_REPO}/app/src/main/resources/static"

if [ "$SKIP_WEB" -eq 0 ]; then
  command -v npm >/dev/null 2>&1 || { msg_err "未找到 npm"; exit 1; }
  cd "${PA_REPO}/web"
  # .env / .env.development / .env.production 已直接位于 web/ 根目录，无需软链
  msg_info "npm ci && npm run build"
  [ -d node_modules ] || npm ci
  npm run build
  msg_ok "web 产物 -> ${WEB_DIST}/"
else
  msg_warn "跳过 web 构建 (--skip-web)"
fi

# --------------------- 2. cp web/dist → app/static ----------
# 思路：让 SpringBoot 单 jar 自托管前端 → 不依赖 nginx。
#   - 目标目录每次 build 都先清空，避免上一次的旧 hash chunk 残留
#   - 用 -a 保留 mtime，方便 mvn 增量；用 trailing /. 同时复制 .* 文件（favicon 等）
if [ "$SKIP_STATIC" -eq 1 ] || [ "$SKIP_WEB" -eq 1 ]; then
  msg_warn "跳过把 dist 拷贝到 app/static (--skip-static 或 --skip-web)"
else
  if [ ! -d "${WEB_DIST}" ] || [ -z "$(ls -A "${WEB_DIST}" 2>/dev/null)" ]; then
    msg_err "web/dist 不存在或为空，无法拷贝到 app/static"; exit 1
  fi
  ensure_dir "${STATIC_DIR}"
  # 清掉旧文件（保留目录本身，让 mvn 不会因为目录消失重建）
  find "${STATIC_DIR}" -mindepth 1 -delete
  cp -a "${WEB_DIST}/." "${STATIC_DIR}/"
  msg_ok "dist -> ${STATIC_DIR}/ ($(find "${STATIC_DIR}" -type f | wc -l | tr -d ' ') files)"
fi

# --------------------- 3. app + spark-etl --------------------
if [ "$SKIP_APP" -eq 0 ] || [ "$SKIP_ETL" -eq 0 ]; then
  command -v mvn >/dev/null 2>&1 || { msg_err "未找到 mvn，请确认 ${MAVEN_HOME}/bin 在 PATH 中"; exit 1; }
  modules=()
  [ "$SKIP_APP" -eq 0 ] && modules+=("app")
  [ "$SKIP_ETL" -eq 0 ] && modules+=("spark-etl")
  IFS=','; mod_csv="${modules[*]}"; unset IFS
  cd "${PA_REPO}"
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

msg_ok "构建完成 —— SpringBoot fat-jar 内置 Vue 看板，直接 java -jar 即可访问 http://${PA_HOST}:${PORT_APP}/"
