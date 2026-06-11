package com.phone.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("ads_profit_anomaly")
public class ProfitAnomaly {
    private LocalDate saleDate;
    private Double grossMargin;
    private Double rollingMargin30d;
    private Double deviationRatio;
    private Boolean isAnomaly;
    private String anomalyLevel;
}
