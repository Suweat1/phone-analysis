package com.phone.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 实时告警消息：
 *   - SpringBoot 模拟器生成 → Kafka phone_alert
 *   - SpringBoot 自己又消费回来 → SSE 推 Vue
 *   - 同步落 Redis（key = RedisKeys.alertRecentList）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertMessage {

    /** 雪花/UUID，由生产者生成 */
    private String alertId;

    /** profit_anomaly / margin_drop / model_low_contrib / channel_drop ... */
    private String type;

    /** high / mid / low */
    private String level;

    /** 中文标题 */
    private String title;

    /** 文本明细 */
    private String content;

    /** 触发时间 */
    private LocalDateTime triggeredAt;

    /** 可选：关联实体（如机型名 / 渠道名） */
    private String relatedEntity;

    /** 数值偏差，便于前端着色 */
    private Double deviation;
}
