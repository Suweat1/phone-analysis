package com.phone.app.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phone.app.dto.AlertMessage;
import com.phone.app.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 订阅 `phone_alert`：把 Spark Streaming（或本应用自己生产）的告警 → SSE 推到 Vue。
 *
 * 设计取舍：
 *  - 让 SpringBoot 自己消费自己产生的告警，是为了与 Spark Streaming 产生的告警走<b>同一条</b>下行链路；
 *    Vue 只需要订阅一个 SSE 端点就能拿到两类告警。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertConsumer {

    private static final ObjectMapper M = new ObjectMapper().findAndRegisterModules();

    private final AlertService alertService;

    @KafkaListener(topics = "${phone.kafka.topic-alert}", groupId = "${spring.kafka.consumer.group-id}")
    public void onAlert(String json, Acknowledgment ack) {
        try {
            AlertMessage msg = M.readValue(json, AlertMessage.class);
            alertService.broadcast(msg);
        } catch (Exception e) {
            log.warn("alert decode failed: {}", e.getMessage());
        } finally {
            ack.acknowledge();
        }
    }
}
