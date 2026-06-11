package com.phone.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ads_high_value_model")
public class HighValueModel {
    private Integer rankNo;
    private String brand;
    private String model;
    private Double totalRevenue;
    private Double totalGrossProfit;
    private Double grossMargin;
    private Double avgUserRating;
    private Double qtyGrowthRatio;
    private Double opportunityScore;
}
