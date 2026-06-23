package com.phone.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 全局 KPI 仪表盘项（一行一个 KPI）。 */
@Data
@TableName("ads_ext_kpi_gauge")
public class KpiGauge {
    private String kpiCode;
    private String kpiNameCn;
    private Double kpiValue;
    private Double targetValue;
    private Double warnValue;
    private Double rawValue;
    private String rawUnit;
}
