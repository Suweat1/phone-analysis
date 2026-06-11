package com.phone.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("ads_metric_trend")
public class MetricTrend {
    private LocalDate saleDate;
    private String metricCode;
    private String metricNameCn;
    private Double metricValue;
    private Double momRatio;
    private Double yoyRatio;
}
