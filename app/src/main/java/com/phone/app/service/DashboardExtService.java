package com.phone.app.service;

import com.phone.app.common.RedisKeys;
import com.phone.app.entity.*;
import com.phone.app.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 扩展看板（ads_ext_* 7 张表）的查询服务。
 *
 * 与 {@link DashboardService} 同样的 Redis 缓存策略 —— 但不复用同一个类是因为：
 * 1) 解耦：扩展看板可以在不影响业务 8 张表的前提下迭代；
 * 2) 编译期边界更清晰，新增板块只动这一个 Service。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardExtService {

    private final BrandSummaryMapper      brandSummaryMapper;
    private final KpiGaugeMapper          kpiGaugeMapper;
    private final BrandModelTreeMapper    brandModelTreeMapper;
    private final CalendarHeatMapper      calendarHeatMapper;
    private final BrandMonthHeatMapper    brandMonthHeatMapper;
    private final SankeyEdgeMapper        sankeyEdgeMapper;
    private final BrandPriceBoxMapper     brandPriceBoxMapper;

    private final RedisTemplate<String, Object> redis;
    private final RedisKeys redisKeys;

    public List<BrandSummary>   brandSummary()    { return cache("ext-brand-summary",     brandSummaryMapper::listAll); }
    public List<KpiGauge>       kpiGauge()        { return cache("ext-kpi-gauge",         kpiGaugeMapper::listAll); }
    public List<BrandModelNode> brandModelTree()  { return cache("ext-brand-model-tree",  brandModelTreeMapper::listAll); }
    public List<CalendarHeat>   calendarHeat()    { return cache("ext-calendar-heat",     calendarHeatMapper::listAll); }
    public List<BrandMonthHeat> brandMonthHeat()  { return cache("ext-brand-month-heat",  brandMonthHeatMapper::listAll); }
    public List<SankeyEdge>     salesSankey()     { return cache("ext-sales-sankey",      sankeyEdgeMapper::listAll); }
    public List<BrandPriceBox>  brandPriceBox()   { return cache("ext-brand-price-box",   brandPriceBoxMapper::listAll); }

    // ---------- helpers ----------
    @SuppressWarnings("unchecked")
    private <T> T cache(String block, Supplier<T> loader) {
        String key = redisKeys.dashboard(block);
        try {
            Object v = redis.opsForValue().get(key);
            if (v != null) return (T) v;
            T fresh = loader.get();
            redis.opsForValue().set(key, fresh, redisKeys.dashboardTtlSec(), TimeUnit.SECONDS);
            return fresh;
        } catch (Exception e) {
            log.warn("Redis cache miss/error for key={}, fallback to DB: {}", key, e.getMessage());
            return loader.get();
        }
    }
}
