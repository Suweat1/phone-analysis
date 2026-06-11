package com.phone.app.controller;

import com.phone.app.common.R;
import com.phone.app.config.PhoneConfig;
import com.phone.app.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康 / 自检接口，便于看板启动时验证后端联通性。
 */
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final PhoneConfig cfg;
    private final AlertService alertService;

    @GetMapping
    public R<Map<String, Object>> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("app", "phone-analysis-app");
        info.put("time", LocalDateTime.now());
        info.put("hiveUrl", cfg.getHive().getJdbcUrl());
        info.put("kafkaTopicRaw", cfg.getKafka().getTopicRaw());
        info.put("kafkaTopicAlert", cfg.getKafka().getTopicAlert());
        info.put("simulatorEnabled", cfg.getSimulator().isEnabled());
        info.put("sseSubscribers", alertService.subscriberCount());
        return R.ok(info);
    }
}
