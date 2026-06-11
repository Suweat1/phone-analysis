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

-- ------------------------------------------------------------
-- 9) 字段中英映射字典（供前端反查 / API 文档展示）
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
