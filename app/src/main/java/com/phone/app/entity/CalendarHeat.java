package com.phone.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/** 日营收日历热力 一行一日。 */
@Data
@TableName("ads_ext_calendar_heat")
public class CalendarHeat {
    private LocalDate saleDate;
    private Double totalRevenue;
    private Long   totalQty;
    private Double grossMargin;
}
