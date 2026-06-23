package com.phone.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 品牌大盘汇总（饼图 / 雷达 / 漏斗 用）。
 * 字段顺序与 ads_ext_brand_summary 一致。
 */
@Data
@TableName("ads_ext_brand_summary")
public class BrandSummary {
    private String brand;
    private Double totalRevenue;
    private Long   totalQty;
    private Double totalGrossProfit;
    private Double grossMargin;
    private Double marketingRatio;
    private Double avgUnitPrice;
    private Double avgUserRating;
    private Long   orderCnt;
    private Long   modelCnt;
    private Double revenueShare;
    private Double profitShare;
    // 雷达 5 维（已归一化到 0~1）
    private Double rRevenue;
    private Double rMargin;
    private Double rQty;
    private Double rRating;
    private Double rLowMarketing;
}
