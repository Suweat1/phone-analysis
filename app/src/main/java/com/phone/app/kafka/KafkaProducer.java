package com.phone.app.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phone.app.config.PhoneConfig;
import com.phone.app.dto.AlertMessage;
import com.phone.app.dto.SaleEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka 生产者：业务代码不直接调 KafkaTemplate，而是走本组件确保 topic 名来自 PhoneConfig。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaProducer {

    private static final ObjectMapper M = new ObjectMapper().findAndRegisterModules();

    private final KafkaTemplate<String, String> kafka;
    private final PhoneConfig cfg;

    public void sendSaleEvent(SaleEvent ev) {
        send(cfg.getKafka().getTopicRaw(), ev.getEventId(), ev);
    }

    public void sendAlert(AlertMessage msg) {
        send(cfg.getKafka().getTopicAlert(), msg.getAlertId(), msg);
    }

    private void send(String topic, String key, Object payload) {
        try {
            String json = M.writeValueAsString(payload);
            kafka.send(topic, key, json);
            log.debug("kafka -> {} key={} bytes={}", topic, key, json.length());
        } catch (JsonProcessingException e) {
            log.warn("kafka serialize failed: topic={}, err={}", topic, e.getMessage());
        }
    }
}
