-- ============================================================
-- ADS 层：直接喂看板的「结果表」
--
-- 8 张表对应看板 8 个板块（与 application.yml.phone.dashboard.* 阈值对应）：
--   ads_profit_anomaly          利润异常监测
--   ads_metric_trend            经济指标波动（多指标日序列）
--   ads_low_contrib_model       低贡献机型 TopN
--   ads_low_contrib_channel     低贡献渠道 TopN
--   ads_profit_decomp           利润下滑归因（客单价/销量/成本/营销 四维度）
--   ads_high_value_model        高价值机会机型 TopN
--   ads_segment_top_margin      利润率优异细分市场
--   ads_growth_potential        被低估的增长潜力点（机型 × 时段 × 渠道）
--
-- 所有 ads 表都会由 spark-etl 的 AdsToMysql Job 同步回写 MySQL 同名表，
-- 供 Spring Boot REST 直读。Hive 端保留是为了：
--   1) 历史快照可追溯
--   2) Beeline 直查校对
-- ============================================================

USE phone_ads;

-- ------------------------------------------------------------
-- 1) 利润异常监测
--    日粒度，标记 gross_margin 较 30 天均值的偏差超过阈值的日期
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ads_profit_anomaly;

CREATE TABLE ads_profit_anomaly (
    sale_date           DATE,
    gross_margin        DOUBLE        COMMENT '当日毛利率',
    rolling_margin_30d  DOUBLE        COMMENT '30 日滚动均值',
    deviation_ratio     DOUBLE        COMMENT '(当日 - 均值) / 均值',
    is_anomaly          BOOLEAN       COMMENT '是否异常（按阈值 phone.dashboard.profit-anomaly-threshold 判断）',
    anomaly_level       STRING        COMMENT 'high / mid / low / normal'
)
COMMENT 'ADS 利润异常监测'
STORED AS PARQUET
TBLPROPERTIES ('parquet.compression' = 'SNAPPY');

-- ------------------------------------------------------------
-- 2) 经济指标波动（多指标日序列）
--    长表设计：metric_code = revenue / qty / unit_price / cost /
--              marketing / gross_profit / gross_margin / marketing_ratio
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ads_metric_trend;

CREATE TABLE ads_metric_trend (
    sale_date           DATE,
    metric_code         STRING        COMMENT '指标代号',
    metric_name_cn      STRING        COMMENT '中文显示名',
    metric_value        DOUBLE,
    mom_ratio           DOUBLE        COMMENT '环比 (vs 前一日)',
    yoy_ratio           DOUBLE        COMMENT '同比 (vs 去年同日，无则 NULL)'
)
COMMENT 'ADS 经济指标日序列波动'
STORED AS PARQUET
TBLPROPERTIES ('parquet.compression' = 'SNAPPY');

-- ------------------------------------------------------------
-- 3) 低贡献机型 TopN（按累计毛利倒序取末尾）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ads_low_contrib_model;

CREATE TABLE ads_low_contrib_model (
    rank_no             INT,
    brand               STRING,
    model               STRING,
    total_revenue       DOUBLE,
    total_gross_profit  DOUBLE,
    gross_margin        DOUBLE,
    contribution_ratio  DOUBLE        COMMENT '毛利 / 全量毛利总额'
)
COMMENT 'ADS 低贡献机型 TopN'
STORED AS PARQUET
TBLPROPERTIES ('parquet.compression' = 'SNAPPY');

-- ------------------------------------------------------------
-- 4) 低贡献渠道 TopN
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ads_low_contrib_channel;

CREATE TABLE ads_low_contrib_channel (
    rank_no             INT,
    promotion           STRING,
    total_revenue       DOUBLE,
    total_gross_profit  DOUBLE,
    gross_margin        DOUBLE,
    marketing_ratio     DOUBLE,
    contribution_ratio  DOUBLE        COMMENT '毛利 / 全量毛利总额'
)
COMMENT 'ADS 低贡献渠道 TopN'
STORED AS PARQUET
TBLPROPERTIES ('parquet.compression' = 'SNAPPY');

-- ------------------------------------------------------------
-- 5) 利润下滑归因（同/环比，4 维度贡献分解）
--    数据按月生成；contribution_pct 之和理论上 ≈ 100% (剩余记入 other)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ads_profit_decomp;

CREATE TABLE ads_profit_decomp (
    sale_ym             STRING        COMMENT 'yyyy-MM',
    compare_type        STRING        COMMENT 'mom / yoy',
    base_ym             STRING        COMMENT '对照期 yyyy-MM',
    profit_curr         DOUBLE        COMMENT '当期毛利',
    profit_base         DOUBLE        COMMENT '对照期毛利',
    profit_delta        DOUBLE        COMMENT '当期 - 对照期',
    factor              STRING        COMMENT 'unit_price / sales_qty / cost / marketing / other',
    factor_name_cn      STRING        COMMENT '中文：客单价 / 销量 / 成本 / 营销费用 / 其他',
    contribution        DOUBLE        COMMENT '该因素带来的毛利变化（金额）',
    contribution_pct    DOUBLE        COMMENT '该因素占总变动比例'
)
COMMENT 'ADS 利润下滑归因 (4 维度)'
STORED AS PARQUET
TBLPROPERTIES ('parquet.compression' = 'SNAPPY');

-- ------------------------------------------------------------
-- 6) 高价值机会机型 TopN
--    评分综合：毛利率 + 营收 + 销量增速 + 用户评价
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ads_high_value_model;

CREATE TABLE ads_high_value_model (
    rank_no             INT,
    brand               STRING,
    model               STRING,
    total_revenue       DOUBLE,
    total_gross_profit  DOUBLE,
    gross_margin        DOUBLE,
    avg_user_rating     DOUBLE,
    qty_growth_ratio    DOUBLE        COMMENT '环比销量增速',
    opportunity_score   DOUBLE        COMMENT '综合机会评分（0~1，归一化）'
)
COMMENT 'ADS 高价值机会机型 TopN'
STORED AS PARQUET
TBLPROPERTIES ('parquet.compression' = 'SNAPPY');

-- ------------------------------------------------------------
-- 7) 利润率优异细分市场（品牌 × 城市 × 年龄段 × 会员等级）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ads_segment_top_margin;

CREATE TABLE ads_segment_top_margin (
    rank_no             INT,
    brand               STRING,
    user_city           STRING,
    age_group           STRING,
    user_member_level   STRING,
    total_revenue       DOUBLE,
    total_gross_profit  DOUBLE,
    gross_margin        DOUBLE,
    order_cnt           BIGINT,
    segment_label_cn    STRING        COMMENT '中文展示，如：苹果·上海·30-40·钻石'
)
COMMENT 'ADS 利润率优异细分市场 TopN'
STORED AS PARQUET
TBLPROPERTIES ('parquet.compression' = 'SNAPPY');

-- ------------------------------------------------------------
-- 8) 被低估的增长潜力点（机型 × 时段 × 渠道）
--    潜力定义：销量增速高 但 营销费用率低 但 毛利率正向
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ads_growth_potential;

CREATE TABLE ads_growth_potential (
    rank_no             INT,
    brand               STRING,
    model               STRING,
    sale_ym             STRING        COMMENT 'yyyy-MM',
    promotion           STRING,
    total_revenue       DOUBLE,
    total_gross_profit  DOUBLE,
    gross_margin        DOUBLE,
    marketing_ratio     DOUBLE,
    qty_growth_ratio    DOUBLE,
    potential_score     DOUBLE        COMMENT '潜力评分（0~1）'
)
COMMENT 'ADS 增长潜力点 TopN'
STORED AS PARQUET
TBLPROPERTIES ('parquet.compression' = 'SNAPPY');
