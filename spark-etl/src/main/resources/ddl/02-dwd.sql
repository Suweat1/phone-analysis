-- ============================================================
-- DWD 层：明细清洗 + 派生指标
--
-- 与 ODS 的差异：
-- 1) 加 8 个派生指标：营收 / 总成本 / 各类成本占比 / 毛利 / 毛利率
-- 2) 加 5 个分桶维度：sale_year/sale_month/sale_quarter/sale_dow/age_group
-- 3) 字符串维度统一去空白；空 promotion 归为 '无'
-- 4) 按 sale_date 的年月做静态分区（dt='yyyy-MM'），方便按月扫
--
-- 派生口径：
--   revenue              = unit_price * sales_qty
--   total_cost           = production_cost + marketing_cost_total
--                        + logistics_cost + platform_commission_total
--                        + after_sales_cost
--   gross_profit         = revenue - total_cost
--   gross_margin         = gross_profit / revenue        (revenue=0 时为 0)
--   marketing_cost_ratio = marketing_cost_total / revenue
--   accessory_attach     = accessory_sales_total / revenue
--   warranty_attach      = warranty_total_sales / revenue
-- ============================================================

USE phone_dwd;

DROP TABLE IF EXISTS dwd_phone_sales;

CREATE TABLE dwd_phone_sales (
    -- 维度（来自 ODS）
    sale_date                   DATE        COMMENT '日期',
    sale_year                   INT         COMMENT '年',
    sale_month                  INT         COMMENT '月',
    sale_quarter                INT         COMMENT '季度',
    sale_dow                    INT         COMMENT '星期(1=周一)',
    brand                       STRING      COMMENT '品牌',
    model                       STRING      COMMENT '型号',
    processor                   STRING      COMMENT '处理器性能',
    camera_pixel_wan            INT         COMMENT '摄像头像素(万)',
    battery_capacity_mah        INT         COMMENT '电池续航(mAh)',
    screen_size_inch            DOUBLE      COMMENT '屏幕尺寸(英寸)',
    storage_gb                  INT         COMMENT '存储容量(GB)',
    refresh_rate_hz             INT         COMMENT '屏幕刷新率(Hz)',
    os                          STRING      COMMENT '操作系统',
    promotion                   STRING      COMMENT '促销活动(空值归为"无")',
    user_rating                 INT         COMMENT '用户评价',

    -- 销售
    unit_price                  INT         COMMENT '客单价',
    sales_qty                   INT         COMMENT '销量',
    revenue                     DOUBLE      COMMENT '营收 = 客单价 * 销量',

    -- 成本（明细 + 合计）
    production_cost             DOUBLE      COMMENT '生产成本',
    marketing_cost_total        DOUBLE      COMMENT '总营销成本',
    logistics_cost              DOUBLE      COMMENT '物流成本',
    platform_commission_total   DOUBLE      COMMENT '总平台佣金',
    after_sales_cost            DOUBLE      COMMENT '售后服务成本',
    total_cost                  DOUBLE      COMMENT '总成本（5 项之和）',

    -- 利润
    gross_profit                DOUBLE      COMMENT '毛利 = 营收 - 总成本',
    gross_margin                DOUBLE      COMMENT '毛利率 = 毛利 / 营收',

    -- 派生比率
    marketing_cost_ratio        DOUBLE      COMMENT '营销费用率 = 总营销成本 / 营收',
    accessory_attach            DOUBLE      COMMENT '配件附加率 = 总配件销售额 / 营收',
    warranty_attach             DOUBLE      COMMENT '延保附加率 = 总延保服务额 / 营收',

    -- 配件 / 延保（保留明细，方便后续单独分析）
    case_sales                  DOUBLE      COMMENT '手机壳销售额',
    earphone_sales              DOUBLE      COMMENT '耳机销售额',
    charger_sales               DOUBLE      COMMENT '充电器销售额',
    cable_sales                 DOUBLE      COMMENT '数据线销售额',
    screen_protector_sales      DOUBLE      COMMENT '保护膜销售额',
    accessory_sales_total       DOUBLE      COMMENT '总配件销售额',
    warranty_1y_sales           DOUBLE      COMMENT '1年延保销售额',
    warranty_2y_sales           DOUBLE      COMMENT '2年延保销售额',
    accident_insurance_sales    DOUBLE      COMMENT '意外险销售额',
    screen_insurance_sales      DOUBLE      COMMENT '碎屏险销售额',
    warranty_total_sales        INT         COMMENT '总延保服务额',

    -- 用户画像
    user_city                   STRING      COMMENT '用户所在城市',
    user_age                    INT         COMMENT '用户年龄',
    age_group                   STRING      COMMENT '年龄段(<20/20-30/30-40/40-50/50+)',
    user_gender                 STRING      COMMENT '用户性别',
    user_member_level           STRING      COMMENT '用户会员等级'
)
COMMENT 'DWD 明细：派生营收/总成本/毛利/毛利率 + 时间与人群分桶'
PARTITIONED BY (dt STRING COMMENT '分区字段：yyyy-MM')
STORED AS PARQUET
TBLPROPERTIES (
    'parquet.compression' = 'SNAPPY'
);
