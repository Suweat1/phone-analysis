-- ============================================================
-- MySQL 看板回写表
--
-- 部署：登录 MySQL 后执行
--   mysql -uroot -p123456 phone_analysis < 05-mysql-ads.sql
--
-- 表结构与 Hive phone_ads 的 8 张表保持字段名 / 顺序一致，
-- Spark AdsToMysql Job 用 overwrite 模式整表替换。
-- 字段中文名见 scripts/column_mapping.py 与 ads_*_dict 表。
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- 1) 利润异常监测
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ads_profit_anomaly;
CREATE TABLE ads_profit_anomaly (
    sale_date           DATE          NOT NULL,
    gross_margin        DOUBLE,
    rolling_margin_30d  DOUBLE,
    deviation_ratio     DOUBLE,
    is_anomaly          TINYINT(1),
    anomaly_level       VARCHAR(16),
    PRIMARY KEY (sale_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='利润异常监测';

-- ------------------------------------------------------------
-- 2) 经济指标日序列
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ads_metric_trend;
CREATE TABLE ads_metric_trend (
    sale_date           DATE          NOT NULL,
    metric_code         VARCHAR(32)   NOT NULL,
    metric_name_cn      VARCHAR(64),
    metric_value        DOUBLE,
    mom_ratio           DOUBLE,
    yoy_ratio           DOUBLE,
    PRIMARY KEY (sale_date, metric_code),
    KEY idx_metric (metric_code, sale_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经济指标日序列波动';

-- ------------------------------------------------------------
-- 3) 低贡献机型
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ads_low_contrib_model;
CREATE TABLE ads_low_contrib_model (
    rank_no             INT           NOT NULL,
    brand               VARCHAR(64),
    model               VARCHAR(128),
    total_revenue       DOUBLE,
    total_gross_profit  DOUBLE,
    gross_margin        DOUBLE,
    contribution_ratio  DOUBLE,
    PRIMARY KEY (rank_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低贡献机型 TopN';

-- ------------------------------------------------------------
-- 4) 低贡献渠道
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ads_low_contrib_channel;
CREATE TABLE ads_low_contrib_channel (
    rank_no             INT           NOT NULL,
    promotion           VARCHAR(64),
    total_revenue       DOUBLE,
    total_gross_profit  DOUBLE,
    gross_margin        DOUBLE,
    marketing_ratio     DOUBLE,
    contribution_ratio  DOUBLE,
    PRIMARY KEY (rank_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低贡献渠道 TopN';

-- ------------------------------------------------------------
-- 5) 利润下滑归因
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ads_profit_decomp;
CREATE TABLE ads_profit_decomp (
    sale_ym             VARCHAR(7)    NOT NULL,
    compare_type        VARCHAR(8)    NOT NULL COMMENT 'mom/yoy',
    base_ym             VARCHAR(7),
    profit_curr         DOUBLE,
    profit_base         DOUBLE,
    profit_delta        DOUBLE,
    factor              VARCHAR(32)   NOT NULL,
    factor_name_cn      VARCHAR(32),
    contribution        DOUBLE,
    contribution_pct    DOUBLE,
    PRIMARY KEY (sale_ym, compare_type, factor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='利润下滑归因 (4 维度)';

-- ------------------------------------------------------------
-- 6) 高价值机型
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ads_high_value_model;
CREATE TABLE ads_high_value_model (
    rank_no             INT           NOT NULL,
    brand               VARCHAR(64),
    model               VARCHAR(128),
    total_revenue       DOUBLE,
    total_gross_profit  DOUBLE,
    gross_margin        DOUBLE,
    avg_user_rating     DOUBLE,
    qty_growth_ratio    DOUBLE,
    opportunity_score   DOUBLE,
    PRIMARY KEY (rank_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='高价值机型 TopN';

-- ------------------------------------------------------------
-- 7) 利润率优异细分市场
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ads_segment_top_margin;
CREATE TABLE ads_segment_top_margin (
    rank_no             INT           NOT NULL,
    brand               VARCHAR(64),
    user_city           VARCHAR(64),
    age_group           VARCHAR(16),
    user_member_level   VARCHAR(32),
    total_revenue       DOUBLE,
    total_gross_profit  DOUBLE,
    gross_margin        DOUBLE,
    order_cnt           BIGINT,
    segment_label_cn    VARCHAR(128),
    PRIMARY KEY (rank_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='利润率优异细分市场';

-- ------------------------------------------------------------
-- 8) 增长潜力点
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ads_growth_potential;
CREATE TABLE ads_growth_potential (
    rank_no             INT           NOT NULL,
    brand               VARCHAR(64),
    model               VARCHAR(128),
    sale_ym             VARCHAR(7),
    promotion           VARCHAR(64),
    total_revenue       DOUBLE,
    total_gross_profit  DOUBLE,
    gross_margin        DOUBLE,
    marketing_ratio     DOUBLE,
    qty_growth_ratio    DOUBLE,
    potential_score     DOUBLE,
    PRIMARY KEY (rank_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='增长潜力点 TopN';

-- ============================================================
-- 扩展看板表（ads_ext_*）—— 服务于 Vue 端多类型 ECharts
-- 与 Hive 04-ads.sql 第 9~15 节字段保持一致
-- ============================================================

-- 9) 品牌大盘汇总
DROP TABLE IF EXISTS ads_ext_brand_summary;
CREATE TABLE ads_ext_brand_summary (
    brand               VARCHAR(64)   NOT NULL,
    total_revenue       DOUBLE,
    total_qty           BIGINT,
    total_gross_profit  DOUBLE,
    gross_margin        DOUBLE,
    marketing_ratio     DOUBLE,
    avg_unit_price      DOUBLE,
    avg_user_rating     DOUBLE,
    order_cnt           BIGINT,
    model_cnt           BIGINT,
    revenue_share       DOUBLE,
    profit_share        DOUBLE,
    r_revenue           DOUBLE,
    r_margin            DOUBLE,
    r_qty               DOUBLE,
    r_rating            DOUBLE,
    r_low_marketing     DOUBLE,
    PRIMARY KEY (brand)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌大盘汇总（饼/雷达/漏斗）';

-- 10) 全局 KPI 仪表盘
DROP TABLE IF EXISTS ads_ext_kpi_gauge;
CREATE TABLE ads_ext_kpi_gauge (
    kpi_code            VARCHAR(32)   NOT NULL,
    kpi_name_cn         VARCHAR(64),
    kpi_value           DOUBLE,
    target_value        DOUBLE,
    warn_value          DOUBLE,
    raw_value           DOUBLE,
    raw_unit            VARCHAR(16),
    PRIMARY KEY (kpi_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局 KPI 仪表盘（gauge）';

-- 11) 品牌×机型 层级
DROP TABLE IF EXISTS ads_ext_brand_model_tree;
CREATE TABLE ads_ext_brand_model_tree (
    brand               VARCHAR(64)   NOT NULL,
    model               VARCHAR(128)  NOT NULL,
    total_revenue       DOUBLE,
    total_gross_profit  DOUBLE,
    gross_margin        DOUBLE,
    total_qty           BIGINT,
    PRIMARY KEY (brand, model)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌→机型层级（Treemap/Sunburst）';

-- 12) 日历热力（按日营收）
DROP TABLE IF EXISTS ads_ext_calendar_heat;
CREATE TABLE ads_ext_calendar_heat (
    sale_date           DATE          NOT NULL,
    total_revenue       DOUBLE,
    total_qty           BIGINT,
    gross_margin        DOUBLE,
    PRIMARY KEY (sale_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日营收日历热力';

-- 13) 品牌×月份 热力
DROP TABLE IF EXISTS ads_ext_brand_month_heat;
CREATE TABLE ads_ext_brand_month_heat (
    brand               VARCHAR(64)   NOT NULL,
    sale_ym             VARCHAR(7)    NOT NULL,
    total_revenue       DOUBLE,
    total_gross_profit  DOUBLE,
    gross_margin        DOUBLE,
    PRIMARY KEY (brand, sale_ym)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌×月份矩形热力';

-- 14) 桑基图边
DROP TABLE IF EXISTS ads_ext_sales_sankey;
CREATE TABLE ads_ext_sales_sankey (
    layer_idx           INT           NOT NULL,
    source              VARCHAR(64)   NOT NULL,
    target              VARCHAR(64)   NOT NULL,
    `value`             DOUBLE,
    PRIMARY KEY (layer_idx, source, target)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='桑基图边（渠道→品牌→年龄段）';

-- 15) 品牌客单价箱线
DROP TABLE IF EXISTS ads_ext_brand_price_box;
CREATE TABLE ads_ext_brand_price_box (
    brand               VARCHAR(64)   NOT NULL,
    q_min               DOUBLE,
    q1                  DOUBLE,
    q_median            DOUBLE,
    q3                  DOUBLE,
    q_max               DOUBLE,
    outliers            VARCHAR(256),
    PRIMARY KEY (brand)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌客单价分位（boxplot）';

-- ------------------------------------------------------------
-- 16) 字段中英映射字典（供前端反查 / API 文档展示）
--    数据由 spark-etl 启动时一次性写入，与 scripts/column_mapping.py 同源
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ads_column_dict;
CREATE TABLE ads_column_dict (
    column_en           VARCHAR(64)   NOT NULL,
    column_cn           VARCHAR(64)   NOT NULL,
    layer               VARCHAR(16)              COMMENT 'ods/dwd/dws/ads',
    category            VARCHAR(32)              COMMENT '维度分类',
    PRIMARY KEY (column_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段中英映射字典';

SET FOREIGN_KEY_CHECKS = 1;
