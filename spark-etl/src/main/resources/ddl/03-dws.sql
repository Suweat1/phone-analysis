-- ============================================================
-- DWS 层：多维度聚合（直接服务于 ads 与即席查询）
--
-- 6 张聚合宽表：
--   dws_sales_daily         按日聚合（看板趋势线）
--   dws_sales_monthly       按月聚合（同比 / 环比）
--   dws_sales_by_brand      按品牌聚合
--   dws_sales_by_model      按机型聚合（看板"机型贡献"用）
--   dws_sales_by_channel    按渠道(promotion)聚合（看板"渠道贡献"用）
--   dws_sales_by_segment    按 [品牌×城市×年龄段×会员等级] 细分市场聚合
--
-- 所有聚合指标统一口径：
--   total_revenue       SUM(revenue)
--   total_qty           SUM(sales_qty)
--   avg_unit_price      total_revenue / total_qty
--   total_cost          SUM(total_cost)
--   total_marketing     SUM(marketing_cost_total)
--   total_gross_profit  SUM(gross_profit)
--   gross_margin        total_gross_profit / total_revenue
--   marketing_ratio     total_marketing / total_revenue
--   accessory_sales     SUM(accessory_sales_total)
--   warranty_sales      SUM(warranty_total_sales)
--   order_cnt           COUNT(*)
-- ============================================================

USE phone_dws;

-- ------------------------------------------------------------
-- 1) 日粒度
-- ------------------------------------------------------------
DROP TABLE IF EXISTS dws_sales_daily;

CREATE TABLE dws_sales_daily (
    sale_date           DATE,
    total_revenue       DOUBLE,
    total_qty           BIGINT,
    avg_unit_price      DOUBLE,
    total_cost          DOUBLE,
    total_marketing     DOUBLE,
    total_gross_profit  DOUBLE,
    gross_margin        DOUBLE,
    marketing_ratio     DOUBLE,
    accessory_sales     DOUBLE,
    warranty_sales      DOUBLE,
    order_cnt           BIGINT
)
COMMENT 'DWS 日聚合（看板时序）'
STORED AS PARQUET
TBLPROPERTIES ('parquet.compression' = 'SNAPPY');

-- ------------------------------------------------------------
-- 2) 月粒度（看板同比 / 环比）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS dws_sales_monthly;

CREATE TABLE dws_sales_monthly (
    sale_year           INT,
    sale_month          INT,
    sale_ym             STRING        COMMENT '冗余 yyyy-MM 方便前端直读',
    total_revenue       DOUBLE,
    total_qty           BIGINT,
    avg_unit_price      DOUBLE,
    total_cost          DOUBLE,
    total_marketing     DOUBLE,
    total_gross_profit  DOUBLE,
    gross_margin        DOUBLE,
    marketing_ratio     DOUBLE,
    accessory_sales     DOUBLE,
    warranty_sales      DOUBLE,
    order_cnt           BIGINT
)
COMMENT 'DWS 月聚合（同比/环比、利润下滑归因基础）'
STORED AS PARQUET
TBLPROPERTIES ('parquet.compression' = 'SNAPPY');

-- ------------------------------------------------------------
-- 3) 品牌粒度
-- ------------------------------------------------------------
DROP TABLE IF EXISTS dws_sales_by_brand;

CREATE TABLE dws_sales_by_brand (
    brand               STRING,
    total_revenue       DOUBLE,
    total_qty           BIGINT,
    avg_unit_price      DOUBLE,
    total_cost          DOUBLE,
    total_marketing     DOUBLE,
    total_gross_profit  DOUBLE,
    gross_margin        DOUBLE,
    marketing_ratio     DOUBLE,
    accessory_sales     DOUBLE,
    warranty_sales      DOUBLE,
    order_cnt           BIGINT,
    model_cnt           BIGINT        COMMENT 'distinct(model)'
)
COMMENT 'DWS 品牌聚合'
STORED AS PARQUET
TBLPROPERTIES ('parquet.compression' = 'SNAPPY');

-- ------------------------------------------------------------
-- 4) 机型粒度（看板"低贡献机型/高价值机型/潜力机型"用）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS dws_sales_by_model;

CREATE TABLE dws_sales_by_model (
    brand               STRING,
    model               STRING,
    total_revenue       DOUBLE,
    total_qty           BIGINT,
    avg_unit_price      DOUBLE,
    total_cost          DOUBLE,
    total_marketing     DOUBLE,
    total_gross_profit  DOUBLE,
    gross_margin        DOUBLE,
    marketing_ratio     DOUBLE,
    accessory_sales     DOUBLE,
    warranty_sales      DOUBLE,
    order_cnt           BIGINT,
    avg_user_rating     DOUBLE        COMMENT '平均用户评价'
)
COMMENT 'DWS 机型聚合'
STORED AS PARQUET
TBLPROPERTIES ('parquet.compression' = 'SNAPPY');

-- ------------------------------------------------------------
-- 5) 渠道粒度（看板"低贡献渠道"用，promotion 即渠道/活动）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS dws_sales_by_channel;

CREATE TABLE dws_sales_by_channel (
    promotion           STRING        COMMENT '渠道 / 促销活动',
    total_revenue       DOUBLE,
    total_qty           BIGINT,
    avg_unit_price      DOUBLE,
    total_cost          DOUBLE,
    total_marketing     DOUBLE,
    total_gross_profit  DOUBLE,
    gross_margin        DOUBLE,
    marketing_ratio     DOUBLE,
    order_cnt           BIGINT
)
COMMENT 'DWS 渠道聚合'
STORED AS PARQUET
TBLPROPERTIES ('parquet.compression' = 'SNAPPY');

-- ------------------------------------------------------------
-- 6) 细分市场粒度（看板"利润率优异细分市场" + "增长潜力点"用）
--    粒度：品牌 × 城市 × 年龄段 × 会员等级
-- ------------------------------------------------------------
DROP TABLE IF EXISTS dws_sales_by_segment;

CREATE TABLE dws_sales_by_segment (
    brand               STRING,
    user_city           STRING,
    age_group           STRING,
    user_member_level   STRING,
    total_revenue       DOUBLE,
    total_qty           BIGINT,
    avg_unit_price      DOUBLE,
    total_cost          DOUBLE,
    total_gross_profit  DOUBLE,
    gross_margin        DOUBLE,
    order_cnt           BIGINT
)
COMMENT 'DWS 细分市场聚合'
STORED AS PARQUET
TBLPROPERTIES ('parquet.compression' = 'SNAPPY');
