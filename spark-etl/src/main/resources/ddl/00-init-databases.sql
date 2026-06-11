-- ============================================================
-- phone-analysis 数仓四库初始化
-- 执行：
--   beeline -u "jdbc:hive2://phone-analysis:10000/" -n bigdata \
--           -f 00-init-databases.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS phone_ods
COMMENT 'ODS - Operational Data Store, 贴源层（与 parquet 一一对应）';

CREATE DATABASE IF NOT EXISTS phone_dwd
COMMENT 'DWD - Data Warehouse Detail, 明细清洗层（含派生指标）';

CREATE DATABASE IF NOT EXISTS phone_dws
COMMENT 'DWS - Data Warehouse Summary, 多维度聚合层';

CREATE DATABASE IF NOT EXISTS phone_ads
COMMENT 'ADS - Application Data Service, 应用层（直接被看板消费 / 回写 MySQL）';
