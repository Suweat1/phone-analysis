package com.phone.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ads_growth_potential")
public class GrowthPotential {
    private Integer rankNo;
    private String brand;
    private String model;
    private String saleYm;
    private String promotion;
    private Double totalRevenue;
    private Double totalGrossProfit;
    private Double grossMargin;
    private Double marketingRatio;
    private Double qtyGrowthRatio;
    private Double potentialScore;
}
