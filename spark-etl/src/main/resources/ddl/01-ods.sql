-- ============================================================
-- ODS 层：完全贴 data/processed/phone.parquet 的 34 列 schema
-- 存储：HDFS 上的 parquet 外部表，路径由 hdfs.raw-dir 决定
-- 注意：parquet 字段名必须与下面 column name 完全一致（已由 preprocess.py 保证）
-- 派生指标（营收 / 毛利 等）一律不在本层出现 —— 留给 DWD
-- ============================================================

USE phone_ods;

DROP TABLE IF EXISTS ods_phone_sales;

CREATE EXTERNAL TABLE ods_phone_sales (
    -- 维度
    sale_date                   DATE        COMMENT '日期',
    brand                       STRING      COMMENT '品牌',
    model                       STRING      COMMENT '型号',
    processor                   STRING      COMMENT '处理器性能',
    camera_pixel_wan            INT         COMMENT '摄像头像素(万)',
    battery_capacity_mah        INT         COMMENT '电池续航(mAh)',
    screen_size_inch            DOUBLE      COMMENT '屏幕尺寸(英寸)',
    storage_gb                  INT         COMMENT '存储容量(GB)',
    refresh_rate_hz             INT         COMMENT '屏幕刷新率(Hz)',
    os                          STRING      COMMENT '操作系统',
    promotion                   STRING      COMMENT '促销活动',
    user_rating                 INT         COMMENT '用户评价',

    -- 销售
    unit_price                  INT         COMMENT '客单价',
    sales_qty                   INT         COMMENT '销量',

    -- 成本
    production_cost             DOUBLE      COMMENT '生产成本',
    marketing_cost_total        DOUBLE      COMMENT '总营销成本',
    logistics_cost              DOUBLE      COMMENT '物流成本',
    platform_commission_total   DOUBLE      COMMENT '总平台佣金',
    after_sales_cost            DOUBLE      COMMENT '售后服务成本',

    -- 配件销售额
    case_sales                  DOUBLE      COMMENT '手机壳销售额',
    earphone_sales              DOUBLE      COMMENT '耳机销售额',
    charger_sales               DOUBLE      COMMENT '充电器销售额',
    cable_sales                 DOUBLE      COMMENT '数据线销售额',
    screen_protector_sales      DOUBLE      COMMENT '保护膜销售额',
    accessory_sales_total       DOUBLE      COMMENT '总配件销售额',

    -- 延保 / 保险销售额
    warranty_1y_sales           DOUBLE      COMMENT '1年延保销售额',
    warranty_2y_sales           DOUBLE      COMMENT '2年延保销售额',
    accident_insurance_sales    DOUBLE      COMMENT '意外险销售额',
    screen_insurance_sales      DOUBLE      COMMENT '碎屏险销售额',
    warranty_total_sales        INT         COMMENT '总延保服务额',

    -- 用户画像
    user_city                   STRING      COMMENT '用户所在城市',
    user_age                    INT         COMMENT '用户年龄',
    user_gender                 STRING      COMMENT '用户性别',
    user_member_level           STRING      COMMENT '用户会员等级'
)
COMMENT 'ODS 贴源：手机销售明细，与 phone.parquet 一一对应'
STORED AS PARQUET
LOCATION '/phone-analysis/raw'
TBLPROPERTIES (
    'parquet.compression' = 'SNAPPY'
);

-- 修复元数据（external 表 + 已有数据时立即可查）
MSCK REPAIR TABLE ods_phone_sales;
