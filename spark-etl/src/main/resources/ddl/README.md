# Hive / MySQL DDL

本目录是 phone-analysis 数仓四层 + MySQL 回写层的 **唯一 DDL 真实源**。

## 文件清单

| 文件 | 目标 | 由谁/何时执行 |
|---|---|---|
| `00-init-databases.sql` | 创建 `phone_ods/dwd/dws/ads` 4 个 Hive 库 | 部署一次 |
| `01-ods.sql` | ODS 贴源表（外部表，指向 HDFS `/phone-analysis/raw/`） | 部署一次 |
| `02-dwd.sql` | DWD 明细 + 派生指标，按月分区 | 部署一次 |
| `03-dws.sql` | DWS 6 张聚合宽表（日/月/品牌/机型/渠道/细分市场） | 部署一次 |
| `04-ads.sql` | ADS 8 张看板结果表 | 部署一次 |
| `05-mysql-ads.sql` | MySQL `phone_analysis` 库的 8 张回写表 + 字段字典 | 部署一次 |
| `06-streaming.sql` | 实时告警归档表 + 分钟级实时聚合表（流式 Job 用） | 部署一次 |

## 执行命令

### Hive 部分

```bash
# 4 个 .sql 顺序执行（Beeline）
for f in 00-init-databases.sql 01-ods.sql 02-dwd.sql 03-dws.sql 04-ads.sql; do
  beeline -u "jdbc:hive2://phone-analysis:10000/" -n bigdata \
          -f /home/bigdata/phone-analysis/spark-etl/src/main/resources/ddl/$f
done
```

### MySQL 部分

```bash
mysql -uroot -p123456 phone_analysis \
  < /home/bigdata/phone-analysis/spark-etl/src/main/resources/ddl/05-mysql-ads.sql
```

> 上述路径假设运行机已 `git pull` 到 `/home/bigdata/phone-analysis/`。

## 设计要点

1. **ODS 严格贴源**：字段名 / 顺序 / 类型与 `data/processed/phone.parquet` 一一对应（由 `scripts/preprocess.py` 保证）。
2. **DWD 派生口径单一**：营收 / 总成本 / 毛利 / 毛利率 / 营销费率 / 配件附加率 / 延保附加率 全部在 DWD 算清，下游禁止重复定义。
3. **DWS 聚合指标统一**：6 张表共用同一套字段命名（`total_revenue / total_qty / gross_margin / ...`），便于看板拼接。
4. **ADS 与看板板块 1:1**：8 张表对应看板 8 个板块；列名与 [config/app/application.yml](../../../../config/app/application.yml) 的 `phone.dashboard.*` 阈值字段保持语义一致。
5. **MySQL 回写为整表 OVERWRITE**：`AdsToMysql` Job 用 `mode("overwrite")`，所以 MySQL 端的主键设定仅用于看板查询加速，不依赖业务唯一性。
6. **字段字典 `ads_column_dict`**：Spark 启动时把 `column_mapping.py` 的内容刷一遍进去（后续由 spark-etl 实现），SpringBoot 通过它给前端做中英映射。
