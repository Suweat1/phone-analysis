package com.phone.app.common;

import com.phone.app.config.PhoneConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis Key 与 TTL 的统一生成器（带 PhoneConfig 注入的前缀）。
 *
 * <p>所有业务代码 <b>必须</b> 走本工具拼 key，禁止散落硬编码 "phone:xxx"。
 */
@Component
public class RedisKeys {

    private final String prefix;
    private final Duration dashboardTtl;
    private final Duration alertTtl;

    @Autowired
    public RedisKeys(PhoneConfig cfg) {
        this.prefix = cfg.getRedis().getKeyPrefix();
        this.dashboardTtl = Duration.ofSeconds(cfg.getRedis().getDashboardTtlSeconds());
        this.alertTtl = Duration.ofSeconds(cfg.getRedis().getAlertTtlSeconds());
    }

    public String dashboard(String block) {
        return prefix + "dashboard:" + block;
    }

    public String alertRecentList() {
        return prefix + "alert:recent";
    }

    public String alertById(String alertId) {
        return prefix + "alert:" + alertId;
    }

    public Duration dashboardTtl() {
        return dashboardTtl;
    }

    public Duration alertTtl() {
        return alertTtl;
    }

    public long dashboardTtlSec() {
        return dashboardTtl.getSeconds();
    }

    public long alertTtlSec() {
        return alertTtl.getSeconds();
    }
}
