package com.phone.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ads_low_contrib_channel")
public class LowContribChannel {
    private Integer rankNo;
    private String promotion;
    private Double totalRevenue;
    private Double totalGrossProfit;
    private Double grossMargin;
    private Double marketingRatio;
    private Double contributionRatio;
}
