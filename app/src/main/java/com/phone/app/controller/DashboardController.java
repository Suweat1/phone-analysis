package com.phone.app.controller;

import com.phone.app.common.R;
import com.phone.app.entity.*;
import com.phone.app.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService svc;

    // ---------- 总览 ----------
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(svc.overview());
    }

    // ---------- 1) 利润异常 ----------
    @GetMapping("/profit-anomaly")
    public R<List<ProfitAnomaly>> profitAnomaly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(svc.profitAnomaly(from, to));
    }

    // ---------- 2) 经济指标 ----------
    @GetMapping("/metric/codes")
    public R<List<MetricTrend>> metricCodes() {
        return R.ok(svc.listMetricCodes());
    }

    @GetMapping("/metric/trend")
    public R<List<MetricTrend>> metricTrend(
            @RequestParam String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(svc.metricTrend(code, from, to));
    }

    // ---------- 3 / 4) 低贡献 TopN ----------
    @GetMapping("/low-contrib/model")
    public R<List<LowContribModel>> lowContribModels() {
        return R.ok(svc.lowContribModels());
    }

    @GetMapping("/low-contrib/channel")
    public R<List<LowContribChannel>> lowContribChannels() {
        return R.ok(svc.lowContribChannels());
    }

    // ---------- 5) 利润下滑归因 ----------
    @GetMapping("/profit-decomp")
    public R<Map<String, List<ProfitDecomp>>> profitDecomp(
            @RequestParam(required = false) String ym) {
        return R.ok(svc.profitDecomp(ym));
    }

    @GetMapping("/profit-decomp/months")
    public R<List<String>> recentMonths() {
        return R.ok(svc.recentMonths());
    }

    // ---------- 6) 高价值机型 ----------
    @GetMapping("/high-value/model")
    public R<List<HighValueModel>> highValueModels() {
        return R.ok(svc.highValueModels());
    }

    // ---------- 7) 利润率优异细分市场 ----------
    @GetMapping("/segment/top-margin")
    public R<List<SegmentTopMargin>> segmentTopMargins() {
        return R.ok(svc.segmentTopMargins());
    }

    // ---------- 8) 增长潜力点 ----------
    @GetMapping("/growth-potential")
    public R<List<GrowthPotential>> growthPotentials() {
        return R.ok(svc.growthPotentials());
    }
}
