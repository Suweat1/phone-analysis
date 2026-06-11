package com.phone.app.service;

import com.phone.app.common.RedisKeys;
import com.phone.app.config.PhoneConfig;
import com.phone.app.entity.*;
import com.phone.app.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 看板数据读取：所有"展示用"的聚合数据都走 MySQL ads_* 表，再通过 Redis 缓存若干秒。
 *
 * Redis 缓存策略：
 *  - Key 由 RedisKeys.dashboard(...) 统一拼装；
 *  - TTL 由 phone.redis.dashboardTtlSeconds 控制（默认 300s）；
 *  - 缓存值类型见 RedisConfig 中 GenericJackson2JsonRedisSerializer，含类型信息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProfitAnomalyMapper       profitAnomalyMapper;
    private final MetricTrendMapper         metricTrendMapper;
    private final LowContribModelMapper     lowContribModelMapper;
    private final LowContribChannelMapper   lowContribChannelMapper;
    private final ProfitDecompMapper        profitDecompMapper;
    private final HighValueModelMapper      highValueModelMapper;
    private final SegmentTopMarginMapper    segmentTopMarginMapper;
    private final GrowthPotentialMapper     growthPotentialMapper;

    private final RedisTemplate<String, Object> redis;
    private final RedisKeys redisKeys;
    private final PhoneConfig cfg;

    // ---------- 1) 利润异常 ----------
    public List<ProfitAnomaly> profitAnomaly(LocalDate from, LocalDate to) {
        String key = redisKeys.dashboard("profit-anomaly:"
                + (from == null ? "_" : from) + ":" + (to == null ? "_" : to));
        return cacheList(key, () -> profitAnomalyMapper.findInRange(from, to));
    }

    // ---------- 2) 指标趋势 ----------
    public List<MetricTrend> metricTrend(String code, LocalDate from, LocalDate to) {
        String key = redisKeys.dashboard("metric-trend:" + code + ":"
                + (from == null ? "_" : from) + ":" + (to == null ? "_" : to));
        return cacheList(key, () -> metricTrendMapper.findByCode(code, from, to));
    }

    public List<MetricTrend> listMetricCodes() {
        return cacheList(redisKeys.dashboard("metric-codes"), metricTrendMapper::listMetrics);
    }

    // ---------- 3 / 4) 低贡献 TopN ----------
    public List<LowContribModel> lowContribModels() {
        int n = cfg.getDashboard().getLowContribTopN();
        return cacheList(redisKeys.dashboard("low-contrib-model:" + n),
                () -> lowContribModelMapper.topN(n));
    }

    public List<LowContribChannel> lowContribChannels() {
        int n = cfg.getDashboard().getLowContribTopN();
        return cacheList(redisKeys.dashboard("low-contrib-channel:" + n),
                () -> lowContribChannelMapper.topN(n));
    }

    // ---------- 5) 利润下滑归因 ----------
    public Map<String, List<ProfitDecomp>> profitDecomp(String ym) {
        Map<String, List<ProfitDecomp>> result = new LinkedHashMap<>();
        result.put("mom", cacheList(redisKeys.dashboard("profit-decomp:mom:" + ym),
                () -> profitDecompMapper.find("mom", ym)));
        result.put("yoy", cacheList(redisKeys.dashboard("profit-decomp:yoy:" + ym),
                () -> profitDecompMapper.find("yoy", ym)));
        return result;
    }

    public List<String> recentMonths() {
        return cacheList(redisKeys.dashboard("recent-months"),
                profitDecompMapper::recentMonths);
    }

    // ---------- 6) 高价值机型 ----------
    public List<HighValueModel> highValueModels() {
        int n = cfg.getDashboard().getHighValueTopN();
        return cacheList(redisKeys.dashboard("high-value-model:" + n),
                () -> highValueModelMapper.topN(n));
    }

    // ---------- 7) 利润率优异细分市场 ----------
    public List<SegmentTopMargin> segmentTopMargins() {
        int n = cfg.getDashboard().getHighValueTopN();
        return cacheList(redisKeys.dashboard("segment-top-margin:" + n),
                () -> segmentTopMarginMapper.topN(n));
    }

    // ---------- 8) 增长潜力点 ----------
    public List<GrowthPotential> growthPotentials() {
        int n = cfg.getDashboard().getHighValueTopN();
        return cacheList(redisKeys.dashboard("growth-potential:" + n),
                () -> growthPotentialMapper.topN(n));
    }

    // ---------- 综合首屏 ----------
    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lowContribModels",   lowContribModels());
        data.put("lowContribChannels", lowContribChannels());
        data.put("highValueModels",    highValueModels());
        data.put("segmentTopMargins",  segmentTopMargins());
        data.put("growthPotentials",   growthPotentials());
        return data;
    }

    // ---------- helpers ----------
    @SuppressWarnings("unchecked")
    private <T> T cacheList(String key, Supplier<T> loader) {
        try {
            Object v = redis.opsForValue().get(key);
            if (v != null) return (T) v;
            T fresh = loader.get();
            redis.opsForValue().set(key, fresh,
                    redisKeys.dashboardTtlSec(), TimeUnit.SECONDS);
            return fresh;
        } catch (Exception e) {
            // Redis 故障时降级直查 DB，不影响看板
            log.warn("Redis cache miss/error for key={}, fallback to DB: {}", key, e.getMessage());
            return loader.get();
        }
    }
}
