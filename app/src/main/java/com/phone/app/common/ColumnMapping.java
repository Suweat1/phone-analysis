package com.phone.app.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 字段中英映射 —— Java 端的真实源镜像。
 *
 * <p>与以下两份保持严格一致（新增/改字段必须同步三处）：
 * <ul>
 *   <li>{@code scripts/column_mapping.py}（预处理脚本）</li>
 *   <li>{@code com.phone.etl.common.ColumnMapping}（Scala/Spark 端）</li>
 * </ul>
 *
 * <p>用途：当 ETL 任务尚未刷新 MySQL `ads_column_dict` 时，前端的字段中文展示
 * 不至于全部空白；同时给本地单测一份兜底。运行时优先以 `ads_column_dict` 为准。
 */
public final class ColumnMapping {

    private ColumnMapping() { /* utility */ }

    /** 英文 → 中文（普通字段 + 派生 + 单位拆分列） */
    public static final Map<String, String> EN_TO_CN;

    /** 英文 → 数仓层（ods/dwd/dws/ads/unknown） */
    public static final Map<String, String> EN_TO_LAYER;

    /** 英文 → 类目（time/product/user/channel/cost/sales/metric/derived） */
    public static final Map<String, String> EN_TO_CATEGORY;

    static {
        Map<String, String> en = new HashMap<>();
        // ods 普通字段
        en.put("sale_date", "日期");
        en.put("brand", "品牌");
        en.put("model", "型号");
        en.put("processor", "处理器性能");
        en.put("os", "操作系统");
        en.put("promotion", "促销活动");
        en.put("user_rating", "用户评价");
        en.put("unit_price", "客单价");
        en.put("sales_qty", "销量");
        en.put("production_cost", "生产成本");
        en.put("marketing_cost_total", "总营销成本");
        en.put("logistics_cost", "物流成本");
        en.put("platform_commission_total", "总平台佣金");
        en.put("after_sales_cost", "售后服务成本");
        en.put("case_sales", "手机壳销售额");
        en.put("earphone_sales", "耳机销售额");
        en.put("charger_sales", "充电器销售额");
        en.put("cable_sales", "数据线销售额");
        en.put("screen_protector_sales", "保护膜销售额");
        en.put("accessory_sales_total", "总配件销售额");
        en.put("warranty_1y_sales", "1年延保销售额");
        en.put("warranty_2y_sales", "2年延保销售额");
        en.put("accident_insurance_sales", "意外险销售额");
        en.put("screen_insurance_sales", "碎屏险销售额");
        en.put("warranty_total_sales", "总延保服务额");
        en.put("user_city", "用户所在城市");
        en.put("user_age", "用户年龄");
        en.put("user_gender", "用户性别");
        en.put("user_member_level", "用户会员等级");

        // 拆单位列
        en.put("camera_pixel_wan", "摄像头像素(万)");
        en.put("battery_capacity_mah", "电池续航(mAh)");
        en.put("screen_size_inch", "屏幕尺寸(英寸)");
        en.put("storage_gb", "存储容量(GB)");
        en.put("refresh_rate_hz", "屏幕刷新率(Hz)");

        // 派生 / 聚合列（覆盖 Spark 端 derivedEnToCn）
        en.put("revenue", "营收");
        en.put("total_cost", "总成本");
        en.put("gross_profit", "毛利");
        en.put("gross_margin", "毛利率");
        en.put("marketing_cost_ratio", "营销费用率");
        en.put("accessory_attach", "配件附加率");
        en.put("warranty_attach", "延保附加率");
        en.put("sale_year", "年");
        en.put("sale_month", "月");
        en.put("sale_quarter", "季度");
        en.put("sale_dow", "星期");
        en.put("sale_ym", "年月");
        en.put("age_group", "年龄段");
        en.put("total_revenue", "营收合计");
        en.put("total_qty", "销量合计");
        en.put("avg_unit_price", "平均客单价");
        en.put("total_marketing", "营销费用合计");
        en.put("total_gross_profit", "毛利合计");
        en.put("marketing_ratio", "营销费用率");
        en.put("accessory_sales", "配件销售合计");
        en.put("warranty_sales", "延保销售合计");
        en.put("order_cnt", "订单数");
        en.put("model_cnt", "机型数");
        en.put("avg_user_rating", "平均用户评价");
        en.put("qty_growth_ratio", "销量环比增速");
        en.put("opportunity_score", "机会评分");
        en.put("potential_score", "潜力评分");
        en.put("contribution_ratio", "贡献占比");
        en.put("rolling_margin_30d", "30日滚动毛利率");
        en.put("deviation_ratio", "偏差率");
        en.put("is_anomaly", "是否异常");
        en.put("anomaly_level", "异常级别");
        en.put("metric_code", "指标代号");
        en.put("metric_value", "指标值");
        en.put("mom_ratio", "环比");
        en.put("yoy_ratio", "同比");
        EN_TO_CN = Collections.unmodifiableMap(en);

        Map<String, String> layer = new HashMap<>();
        EN_TO_CN.keySet().forEach(k -> layer.put(k, inferLayer(k)));
        EN_TO_LAYER = Collections.unmodifiableMap(layer);

        Map<String, String> cat = new HashMap<>();
        EN_TO_CN.keySet().forEach(k -> cat.put(k, inferCategory(k)));
        EN_TO_CATEGORY = Collections.unmodifiableMap(cat);
    }

    private static String inferLayer(String en) {
        switch (en) {
            case "qty_growth_ratio":
            case "opportunity_score":
            case "potential_score":
            case "contribution_ratio":
            case "rolling_margin_30d":
            case "deviation_ratio":
            case "is_anomaly":
            case "anomaly_level":
            case "metric_code":
            case "metric_value":
            case "mom_ratio":
            case "yoy_ratio":
                return "ads";
            case "total_revenue":
            case "total_qty":
            case "avg_unit_price":
            case "total_marketing":
            case "total_gross_profit":
            case "marketing_ratio":
            case "accessory_sales":
            case "warranty_sales":
            case "order_cnt":
            case "model_cnt":
            case "avg_user_rating":
            case "sale_ym":
                return "dws";
            case "revenue":
            case "total_cost":
            case "gross_profit":
            case "gross_margin":
            case "marketing_cost_ratio":
            case "accessory_attach":
            case "warranty_attach":
            case "sale_year":
            case "sale_month":
            case "sale_quarter":
            case "sale_dow":
            case "age_group":
                return "dwd";
            default:
                return "ods";
        }
    }

    private static String inferCategory(String en) {
        switch (en) {
            case "sale_date":
            case "sale_year":
            case "sale_month":
            case "sale_quarter":
            case "sale_dow":
            case "sale_ym":
                return "time";
            case "brand":
            case "model":
            case "processor":
            case "os":
            case "camera_pixel_wan":
            case "battery_capacity_mah":
            case "screen_size_inch":
            case "storage_gb":
            case "refresh_rate_hz":
                return "product";
            case "user_city":
            case "user_age":
            case "user_gender":
            case "user_member_level":
            case "age_group":
            case "user_rating":
                return "user";
            case "promotion":
                return "channel";
            default:
                if (en.endsWith("_cost") || en.contains("commission") || en.contains("marketing")) {
                    return "cost";
                }
                if (en.endsWith("_sales") || en.startsWith("warranty_")
                        || en.contains("accessory") || en.contains("insurance")) {
                    return "sales";
                }
                if (en.equals("revenue") || en.equals("total_revenue") || en.equals("gross_profit")
                        || en.equals("total_gross_profit") || en.equals("gross_margin")
                        || en.equals("unit_price") || en.equals("sales_qty")
                        || en.equals("avg_unit_price") || en.equals("total_qty")
                        || en.equals("total_cost") || en.equals("total_marketing")
                        || en.equals("order_cnt") || en.equals("model_cnt")) {
                    return "metric";
                }
                return "derived";
        }
    }
}
