-- ============================================================
-- 06-streaming.sql —— Spark Structured Streaming 用的两张表
--
-- 1) phone_ads.ads_realtime_alert   实时告警归档（与 Kafka phone_alert 一致 schema）
-- 2) phone_dws.dws_realtime_metric_1min   分钟级实时聚合（留扩展位）
--
-- 与离线 ads_* 表不同，这两张表只追加（append），不重算。
-- 按日分区，方便按日 archive/drop。
-- ============================================================

USE phone_ads;

DROP TABLE IF EXISTS ads_realtime_alert;

CREATE TABLE ads_realtime_alert (
    alert_id          STRING  COMMENT '告警唯一 ID（uuid）',
    alert_type        STRING  COMMENT 'profit_anomaly / big_loss / marketing_burnout / price_outlier / qty_spike',
    alert_level       STRING  COMMENT 'high / mid / low',
    alert_title       STRING  COMMENT '中文标题（直接给 Vue 展示）',
    alert_content     STRING  COMMENT '中文明细',
    related_entity    STRING  COMMENT '关联实体：品牌·机型',
    deviation         DOUBLE  COMMENT '偏差幅度（前端着色用）',

    -- 触发事件回显
    event_id          STRING  COMMENT '原始 SaleEvent.eventId',
    event_time        TIMESTAMP COMMENT '事件时间',
    brand             STRING,
    model             STRING,
    promotion         STRING  COMMENT '渠道',
    unit_price        INT,
    sales_qty         INT,
    revenue           DOUBLE,
    gross_profit      DOUBLE,
    gross_margin      DOUBLE,
    marketing_ratio   DOUBLE,

    -- 处理时间
    triggered_at      TIMESTAMP COMMENT 'Spark 处理时间'
)
COMMENT '实时告警归档（与 Kafka phone_alert 同构）'
PARTITIONED BY (dt STRING COMMENT '日分区 yyyy-MM-dd')
STORED AS PARQUET
TBLPROPERTIES (
    'parquet.compression' = 'SNAPPY'
);


USE phone_dws;

DROP TABLE IF EXISTS dws_realtime_metric_1min;

CREATE TABLE dws_realtime_metric_1min (
    window_start    TIMESTAMP   COMMENT '窗口起始（1 分钟）',
    window_end      TIMESTAMP   COMMENT '窗口结束',
    brand           STRING      COMMENT '品牌',
    model           STRING      COMMENT '机型',
    event_cnt       BIGINT      COMMENT '事件数',
    qty_sum         BIGINT      COMMENT '销量合计',
    revenue_sum     DOUBLE      COMMENT '营收合计',
    profit_sum      DOUBLE      COMMENT '毛利合计',
    avg_margin      DOUBLE      COMMENT '平均毛利率',
    alert_cnt       BIGINT      COMMENT '触发告警数'
)
COMMENT '分钟级实时聚合（留扩展位，看板可选订阅）'
PARTITIONED BY (dt STRING COMMENT '日分区 yyyy-MM-dd')
STORED AS PARQUET
TBLPROPERTIES (
    'parquet.compression' = 'SNAPPY'
);
