package com.phone.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 品牌×月份 毛利率矩形热力 / 河流图。 */
@Data
@TableName("ads_ext_brand_month_heat")
public class BrandMonthHeat {
    private String brand;
    private String saleYm;
    private Double totalRevenue;
    private Double totalGrossProfit;
    private Double grossMargin;
}
