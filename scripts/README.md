# scripts/ — 部署 & 运行脚本

> 本机（macOS）开发时只跑 `preprocess.py`；其余 6 个 `*.sh` 脚本全部在 **运行机 Ubuntu** 上以 `bigdata` 用户执行。
> 所有 shell 脚本共享 `lib/env.sh` 这一唯一全局变量源（端口/路径/密码/家目录），改任何参数只动它。

## 文件总览

| 脚本 | 用途 | 运行位置 |
|---|---|---|
| `preprocess.py` | Excel → Parquet/CSV 预处理（pandas） | 本机（仅 `uv run` 即时建 .venv） |
| `column_mapping.py` | 字段中英映射的 Python 源（写 ColumnDict 时回读） | 被 preprocess 引用 |
| `lib/env.sh` | **全局变量源**（端口/路径/密码/家目录） | 被所有 .sh source |
| `deploy-config.sh` | `git pull` 后把 `config/<组件>/*` link 到组件实际目录 | 运行机 |
| `build-all.sh` | `mvn package` (app+spark-etl) + `npm build` (web) | 运行机 |
| `start-all.sh` | 按顺序启 10 个组件，pid 写到 `/opt/bigdata/data/pid/` | 运行机 |
| `stop-all.sh` | 反向停止 | 运行机 |
| `status.sh` | 端口 + jps + REST 健康一行汇总 | 运行机 |
| `run-pipeline.sh` | hdfs put parquet → Spark ETL → 通知 SpringBoot 刷字典 | 运行机 |
| `start-streaming.sh` | 启动 RawStreamingJob（长进程，Kafka phone_raw → 告警双写） | 运行机 |
| `stop-streaming.sh` | 优雅停 RawStreamingJob | 运行机 |
| `reset-hive-metastore.sh` | 重建 MySQL 上的 `hive_metastore` 库（KEY_CONSTRAINTS_FK4 死锁急救） | 运行机 |

## 首次部署流程（运行机）

```bash
# 0. 仓库 clone
git clone <repo> ~/phone-analysis
cd ~/phone-analysis

# 1. 部署配置（把 config/* 链接到组件目录）
bash scripts/deploy-config.sh

# 2. 构建（首次会比较久）
bash scripts/build-all.sh

# 3. 起所有组件
bash scripts/start-all.sh

# 4. 灌数据 + 跑 ETL（首次需要 --init 建库 + 字段字典）
bash scripts/run-pipeline.sh --init

# 5. 启动实时告警流（长进程；启停独立，不归 PipelineJob）
bash scripts/start-streaming.sh

# 6. 看状态
bash scripts/status.sh
```

## 日常运维

| 场景 | 命令 |
|---|---|
| 拉新代码 + 重新构建 + 重启 app | `git pull && bash scripts/build-all.sh --skip-etl --skip-web && bash scripts/stop-all.sh --only app && bash scripts/start-all.sh --only app` |
| 仅刷数据（口径没变） | `bash scripts/run-pipeline.sh` |
| 重启 Kafka & SpringBoot | `bash scripts/stop-all.sh --only app,kafka && bash scripts/start-all.sh --only kafka,app` |
| 全部停掉 | `bash scripts/stop-all.sh` |
| 看哪些组件挂了 | `bash scripts/status.sh` |

## 参数速查

```bash
./start-all.sh --only mysql,redis           # 只起部分
./start-all.sh --skip app                   # 起其余、不启 app
./build-all.sh --skip-app --skip-web        # 仅 spark-etl
./run-pipeline.sh --init                    # 首次：含 InitSchemaJob
./run-pipeline.sh --no-push                 # 跳过 hdfs put（HDFS 已是最新）
./run-pipeline.sh --no-refresh-app          # 不调 /api/dict/refresh
```

## 路径约定（与 docs/deploy/ 完全一致）

```
${PA_REPO}=~/phone-analysis     ${PA_BASE}=/opt/bigdata
                              ├─ log/<组件>/<组件>.out
                              ├─ data/pid/<组件>.pid
                              ├─ data/jars/{phone-analysis-app.jar, phone-analysis-spark-etl.jar}
                              ├─ service/{jdk,hadoop,hive,kafka,spark,maven,redis}
                              └─ software/  ← 原始 tar.gz
```

## 排错小抄

| 现象 | 原因 | 修法 |
|---|---|---|
| `start-all.sh` 报 `等待端口 :XXX 超时` | 对应组件日志在 `/opt/bigdata/log/<组件>/*.out` | `tail -n 200 /opt/bigdata/log/<组件>/*.out` |
| `stop-all.sh` 反复说 not running，但 jps 还在 | pid 文件丢了 + 进程名 pattern 不匹配 | `pkill -f <关键词>` 手动收尾 |
| `run-pipeline.sh --init` 报 metastore connect refused | `metastore` 没起 / 没就绪 | `bash scripts/start-all.sh --only metastore` 然后等 30s |
| `/api/health` 返回 502/404 | app jar 没构建 / Spring profile 错 | `tail /opt/bigdata/log/app/app.out` |
| 改了密码业务代码不生效 | 仅改了 `lib/env.sh` 不够 | 同步改 `config/app/application.yml` / `config/spark-etl/application.properties` / `config/redis/redis.conf` |
| metastore 启动卡死 / Beeline `recv_drop_table_with_environment_context` 长时间无响应 | KEY_CONSTRAINTS 无索引 + 启动期 ALTER ADD CONSTRAINT FK4 与 hive 连接抢锁（HIVE-21563） | `bash scripts/reset-hive-metastore.sh --yes`，详见 [docs/deploy/05-hive.md §6.1](../docs/deploy/05-hive.md#61-必做-给-key_constraints-加索引hive-21563) |
