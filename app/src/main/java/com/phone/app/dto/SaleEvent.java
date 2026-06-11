package com.phone.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 模拟「销售事件」：由 SpringBoot Scheduler 周期产出，发送到 Kafka phone_raw。
 * 字段命名贴 ods/dwd schema，便于未来 Spark Streaming 消费时直接转 DataFrame。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleEvent {

    private String eventId;
    private LocalDateTime eventTime;

    private String brand;
    private String model;
    private String processor;
    private Integer storageGb;
    private String promotion;       // 渠道 / 促销活动

    private Integer unitPrice;
    private Integer salesQty;
    private Double  productionCost;
    private Double  marketingCostTotal;

    private String userCity;
    private Integer userAge;
    private String userGender;
    private String userMemberLevel;

    /** 派生：营收/毛利预览，方便 SSE 直接显示 */
    private Map<String, Object> derived;
}
