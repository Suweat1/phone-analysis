# -*- coding: utf-8 -*-
"""
中英列名映射：单一真实源。

- preprocess.py 用它把 Excel 中文列名重命名为 snake_case 英文；
- SpringBoot 后端 / Vue 前端在展示时用它做反向查询（英文 -> 中文）以保证
  「数据存英文、可视化展中文」。
- 同时定义了一份「最终列顺序」ORDERED_EN_COLUMNS，作为 Hive ods 表 DDL
  字段顺序的事实依据。
- 五个「规格 + 单位」字段在 preprocess.py 中被拆分为数值列，这里给出的
  仍是英文目标列名（如 camera_pixel_wan），同时也定义了它们对应的
  「中文显示名」。
"""
from __future__ import annotations

# ---------------------------------------------------------------------------
# 1. 普通中文列名 → 英文
# ---------------------------------------------------------------------------
COLUMN_CN_TO_EN: dict[str, str] = {
    # 维度
    "日期":          "sale_date",
    "品牌":          "brand",
    "型号":          "model",
    "处理器性能":    "processor",
    "操作系统":      "os",
    "促销活动":      "promotion",
    "用户评价":      "user_rating",

    # 价格与销量
    "客单价":        "unit_price",
    "销量":          "sales_qty",

    # 成本
    "生产成本":      "production_cost",
    "总营销成本":    "marketing_cost_total",
    "物流成本":      "logistics_cost",
    "总平台佣金":    "platform_commission_total",
    "售后服务成本":  "after_sales_cost",

    # 配件销售
    "手机壳销售额":  "case_sales",
    "耳机销售额":    "earphone_sales",
    "充电器销售额":  "charger_sales",
    "数据线销售额":  "cable_sales",
    "保护膜销售额":  "screen_protector_sales",
    "总配件销售额":  "accessory_sales_total",

    # 延保 / 保险
    "1年延保销售额": "warranty_1y_sales",
    "2年延保销售额": "warranty_2y_sales",
    "意外险销售额":  "accident_insurance_sales",
    "碎屏险销售额":  "screen_insurance_sales",
    "总延保服务额":  "warranty_total_sales",

    # 用户画像
    "用户所在城市":  "user_city",
    "用户年龄":      "user_age",
    "用户性别":      "user_gender",
    "用户会员等级":  "user_member_level",
}

# ---------------------------------------------------------------------------
# 2. 五个「规格 + 单位」拆分后的英文列 → 中文显示名
#    （这些列在源数据里是「带单位字符串」，preprocess.py 拆为纯数值后用此显示）
# ---------------------------------------------------------------------------
SPEC_UNIT_EN_TO_CN_DISPLAY: dict[str, str] = {
    "camera_pixel_wan":       "摄像头像素(万)",
    "battery_capacity_mah":   "电池续航(mAh)",
    "screen_size_inch":       "屏幕尺寸(英寸)",
    "storage_gb":             "存储容量(GB)",
    "refresh_rate_hz":        "屏幕刷新率(Hz)",
}

# ---------------------------------------------------------------------------
# 2.1 扩展看板派生字段（与 spark-etl 的 ads_ext_* 表对齐）
# ---------------------------------------------------------------------------
EXT_EN_TO_CN_DISPLAY: dict[str, str] = {
    "revenue_share":     "营收占比",
    "profit_share":      "毛利占比",
    "r_revenue":         "雷达·营收(归一)",
    "r_margin":          "雷达·毛利率(归一)",
    "r_qty":             "雷达·销量(归一)",
    "r_rating":          "雷达·评价(归一)",
    "r_low_marketing":   "雷达·低营销费率(归一)",
    "kpi_code":          "KPI 代号",
    "kpi_name_cn":       "KPI 名称",
    "kpi_value":         "KPI 当前值",
    "target_value":      "目标值",
    "warn_value":        "告警阈值",
    "raw_value":         "原始数值",
    "raw_unit":          "数值单位",
    "layer_idx":         "桑基层级",
    "q_min":             "最小值",
    "q1":                "下四分位",
    "q_median":          "中位数",
    "q3":                "上四分位",
    "q_max":             "最大值",
    "outliers":          "离群点",
}

# ---------------------------------------------------------------------------
# 3. 完整 英文 → 中文 反向映射（供 Java/JS 展示层使用）
# ---------------------------------------------------------------------------
COLUMN_EN_TO_CN: dict[str, str] = {
    **{en: cn for cn, en in COLUMN_CN_TO_EN.items()},
    **SPEC_UNIT_EN_TO_CN_DISPLAY,
    **EXT_EN_TO_CN_DISPLAY,
}

# ---------------------------------------------------------------------------
# 4. 预处理输出的列顺序 = Hive ods 建表字段顺序
# ---------------------------------------------------------------------------
ORDERED_EN_COLUMNS: list[str] = [
    # 维度
    "sale_date",
    "brand",
    "model",
    "processor",
    "camera_pixel_wan",
    "battery_capacity_mah",
    "screen_size_inch",
    "storage_gb",
    "refresh_rate_hz",
    "os",
    "promotion",
    "user_rating",

    # 销售
    "unit_price",
    "sales_qty",

    # 成本
    "production_cost",
    "marketing_cost_total",
    "logistics_cost",
    "platform_commission_total",
    "after_sales_cost",

    # 配件
    "case_sales",
    "earphone_sales",
    "charger_sales",
    "cable_sales",
    "screen_protector_sales",
    "accessory_sales_total",

    # 延保 / 保险
    "warranty_1y_sales",
    "warranty_2y_sales",
    "accident_insurance_sales",
    "screen_insurance_sales",
    "warranty_total_sales",

    # 用户
    "user_city",
    "user_age",
    "user_gender",
    "user_member_level",
]
