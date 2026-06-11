# web 模块运行手册

> 本机 macOS 没有 java/maven 但有 `node`（>=14）即可本地起 dev server；
> 真正部署在 Ubuntu 虚拟机上 `npm run build` 出 `dist/`，由 SpringBoot 静态资源或 nginx 暴露。

## 1. 安装依赖

```bash
cd ~/phone-analysis/web
# 把仓库 config/web/ 下的 .env 文件软链到本目录，Vue CLI 才能读
ln -sfn ../config/web/.env             .env
ln -sfn ../config/web/.env.development .env.development
ln -sfn ../config/web/.env.production  .env.production

npm install        # 或 npm ci
```

## 2. 本机开发模式（dev server）

```bash
npm run serve
# 默认 http://localhost:8081 ，已通过 vue.config.js 的 devServer.proxy 把 /api 转发到 phone-analysis:8080
```

> 若本机 `/etc/hosts` 没有 phone-analysis 域名，请改 `config/web/.env.development` 的 `VUE_APP_API_PROXY`
> 例如 `VUE_APP_API_PROXY=http://192.168.1.20:8080`

## 3. 生产构建

```bash
npm run build
# 产物：web/dist/  → 可由 SpringBoot 配置静态资源映射，或 cp 到 nginx 的 root 目录
```

## 4. 页面 & 路由

| 路径 | 板块 | 后端接口 |
|---|---|---|
| `/overview`         | 总览（5 张 TopN 拼接） | `/api/dashboard/overview` |
| `/profit-anomaly`   | 利润异常 | `/api/dashboard/profit-anomaly` |
| `/metric-trend`     | 经济指标波动 | `/api/dashboard/metric/codes` + `/metric/trend` |
| `/low-contrib`      | 低贡献机型 / 渠道 | `/api/dashboard/low-contrib/{model,channel}` |
| `/profit-decomp`    | 利润下滑归因 | `/api/dashboard/profit-decomp` + `/months` |
| `/high-value`       | 高价值机型 | `/api/dashboard/high-value/model` |
| `/segment`          | 利润率优异细分 | `/api/dashboard/segment/top-margin` |
| `/growth-potential` | 增长潜力点 | `/api/dashboard/growth-potential` |

右侧固定的「实时告警」面板订阅 SSE `/api/alert/stream`，初次进入会先用 `/api/alert/recent` 回填。

## 5. 字段中英映射

- 启动时 `main.js` 调一次 `/api/dict/columns` 拉全量映射缓存到内存；
- 业务组件用 `import { cn } from '@/utils/dict'`，对任意英文字段反查中文；
- 后端字典优先 MySQL `ads_column_dict`（由 Spark `ColumnDictJob` 写入），失败则回退 Java 内置兜底。

## 6. 调试 SSE

```bash
curl -N http://phone-analysis:8080/api/alert/stream
# 应能看到每 ~100s（5% 概率）一次 event: alert 的输出
```

## 7. 常见问题

| 现象 | 原因 | 修法 |
|---|---|---|
| 看板全部空白，控制台 ERR_CONNECTION_REFUSED | SpringBoot 没起 | `nohup java -jar app.jar ...` |
| 数据存在但中文显示为英文 | `ads_column_dict` 表为空 | 跑 spark-etl `ColumnDictJob` 或 InitSchemaJob |
| 告警永不到达 | EventSimulator 关了 / Kafka 未起 | 检查 `phone.simulator.enabled` 与 Kafka 进程 |
| `dist/` 部署后路由 404 刷新失效 | 使用了 history 模式 | 当前默认 hash 模式无此问题；若改成 history 需 nginx try_files |
