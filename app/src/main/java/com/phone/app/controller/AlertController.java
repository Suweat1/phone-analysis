package com.phone.app.controller;

import com.phone.app.common.R;
import com.phone.app.dto.AlertMessage;
import com.phone.app.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/alert")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    /** SSE 订阅端点：Vue 用 EventSource(`/api/alert/stream`)。 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return alertService.subscribe();
    }

    /** 拉取最近告警（用于初次进入页面 / 断线后回填）。 */
    @GetMapping("/recent")
    public R<List<AlertMessage>> recent() {
        return R.ok(alertService.listRecent());
    }

    /** 监控埋点 */
    @GetMapping("/_status")
    public R<Map<String, Object>> status() {
        Map<String, Object> body = new HashMap<>();
        body.put("subscriberCount", alertService.subscriberCount());
        body.put("recent", alertService.listRecent().size());
        return R.ok(body);
    }
}
