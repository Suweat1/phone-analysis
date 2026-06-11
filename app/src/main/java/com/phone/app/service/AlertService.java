package com.phone.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phone.app.common.RedisKeys;
import com.phone.app.config.PhoneConfig;
import com.phone.app.dto.AlertMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 告警广播：
 *  - 内存 emitter 列表负责 SSE 推送；
 *  - StringRedisTemplate 同步把最近告警写到 Redis（带 TTL），供新连接「回填历史」。
 *
 * Redis 端的存储格式：
 *  - Key:  phone:alert:recent  → LIST，LPUSH JSON 字符串，LTRIM 保留 100 条
 *  - TTL 由 phone.redis.alertTtlSeconds 控制
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private static final int MAX_HISTORY = 100;
    private static final ObjectMapper M = new ObjectMapper()
            .findAndRegisterModules();

    private final StringRedisTemplate redis;
    private final RedisKeys redisKeys;

    @Autowired private PhoneConfig cfg;        // 仅作未来扩展占位

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // ---------- SSE ----------

    public SseEmitter subscribe() {
        // 0 = 不超时；客户端长连接
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        // 立即把最近的若干历史回填给新订阅者
        listRecent().forEach(msg -> safeSend(emitter, msg));
        return emitter;
    }

    public int subscriberCount() {
        return emitters.size();
    }

    // ---------- 广播 ----------

    public void broadcast(AlertMessage msg) {
        persist(msg);
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter e : emitters) {
            if (!safeSend(e, msg)) dead.add(e);
        }
        emitters.removeAll(dead);
    }

    private boolean safeSend(SseEmitter e, AlertMessage msg) {
        try {
            e.send(SseEmitter.event()
                    .id(msg.getAlertId())
                    .name("alert")
                    .data(msg));
            return true;
        } catch (IOException ex) {
            log.debug("sse send failed, drop emitter: {}", ex.getMessage());
            return false;
        }
    }

    // ---------- Redis 持久化（最近 N 条） ----------

    private void persist(AlertMessage msg) {
        try {
            String key = redisKeys.alertRecentList();
            redis.opsForList().leftPush(key, M.writeValueAsString(msg));
            redis.opsForList().trim(key, 0, MAX_HISTORY - 1);
            redis.expire(key, redisKeys.alertTtl());
        } catch (JsonProcessingException e) {
            log.warn("alert serialize failed: {}", e.getMessage());
        }
    }

    public List<AlertMessage> listRecent() {
        List<String> raw = redis.opsForList()
                .range(redisKeys.alertRecentList(), 0, MAX_HISTORY - 1);
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        List<AlertMessage> out = new ArrayList<>(raw.size());
        for (String s : raw) {
            try {
                out.add(M.readValue(s, AlertMessage.class));
            } catch (JsonProcessingException ignored) { /* skip malformed */ }
        }
        return out;
    }
}
