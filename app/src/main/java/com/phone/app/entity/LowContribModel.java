package com.phone.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ads_low_contrib_model")
public class LowContribModel {
    private Integer rankNo;
    private String brand;
    private String model;
    private Double totalRevenue;
    private Double totalGrossProfit;
    private Double grossMargin;
    private Double contributionRatio;
}
