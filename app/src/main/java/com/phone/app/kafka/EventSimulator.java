package com.phone.app.kafka;

import com.phone.app.config.PhoneConfig;
import com.phone.app.dto.AlertMessage;
import com.phone.app.dto.SaleEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 销售事件模拟器：从代码内置枚举里随机组合，定时打入 Kafka phone_raw。
 *
 * <p>开关：phone.simulator.enabled（true/false）；周期：phone.simulator.interval-ms。
 * <p>同时按 5% 概率生成一条 phone_alert，验证下行链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "phone.simulator", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class EventSimulator {

    /** 与原始 Excel 中观察到的真实值保持相近，便于 Spark 端 schema 一致 */
    private static final List<String> BRANDS = Arrays.asList(
            "小米", "vivo", "OPPO", "华为", "苹果", "荣耀", "三星", "realme");

    /** 真实型号示例（精简，避免列表过长）；不做品牌-机型映射，简化处理 */
    private static final List<String> MODELS = Arrays.asList(
            "Xiaomi 13", "Redmi K60", "vivo X90", "OPPO Find X6", "Mate 60",
            "iPhone 15", "Honor 90", "Galaxy S23", "realme GT5");

    private static final List<String> PROCESSORS = Arrays.asList(
            "骁龙8 Gen2", "骁龙8+ Gen1", "天玑9200", "麒麟9000s", "A17 Pro", "Exynos 2200");

    private static final List<String> PROMOTIONS = Arrays.asList(
            "满减", "赠品", "分期", "限时折扣", "无");

    private static final int[] STORAGES = {128, 256, 512, 1024};

    private static final List<String> CITIES = Arrays.asList(
            "北京", "上海", "广州", "深圳", "杭州", "成都",
            "南京", "武汉", "西安", "重庆");

    private static final List<String> GENDERS = Arrays.asList("男", "女");

    private static final List<String> MEMBER_LEVELS = Arrays.asList(
            "普通", "白银", "黄金", "铂金", "钻石");

    private final KafkaProducer kafkaProducer;
    private final PhoneConfig cfg;

    @Scheduled(fixedDelayString = "${phone.simulator.interval-ms:5000}")
    public void tick() {
        int batch = cfg.getSimulator().getBatchSize();
        for (int i = 0; i < batch; i++) {
            kafkaProducer.sendSaleEvent(buildOne());
        }
        log.debug("simulator pushed {} sale events", batch);

        if (ThreadLocalRandom.current().nextDouble() < 0.05) {
            kafkaProducer.sendAlert(buildAlert());
        }
    }

    private SaleEvent buildOne() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int unitPrice = r.nextInt(1500, 13000);
        int qty       = r.nextInt(1, 200);
        double prodCost = unitPrice * qty * (0.55 + r.nextDouble(0.15));
        double mkCost   = unitPrice * qty * (0.05 + r.nextDouble(0.10));

        Map<String, Object> derived = new HashMap<>();
        derived.put("revenue", (long) unitPrice * qty);
        derived.put("gross_profit", (long) unitPrice * qty - prodCost - mkCost);

        return SaleEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventTime(LocalDateTime.now())
                .brand(pick(BRANDS))
                .model(pick(MODELS))
                .processor(pick(PROCESSORS))
                .storageGb(STORAGES[r.nextInt(STORAGES.length)])
                .promotion(pick(PROMOTIONS))
                .unitPrice(unitPrice)
                .salesQty(qty)
                .productionCost(round(prodCost))
                .marketingCostTotal(round(mkCost))
                .userCity(pick(CITIES))
                .userAge(r.nextInt(18, 65))
                .userGender(pick(GENDERS))
                .userMemberLevel(pick(MEMBER_LEVELS))
                .derived(derived)
                .build();
    }

    private AlertMessage buildAlert() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String[] types  = {"profit_anomaly", "margin_drop", "model_low_contrib", "channel_drop"};
        String[] levels = {"high", "mid", "low"};
        String t = types[r.nextInt(types.length)];
        String level = levels[r.nextInt(levels.length)];
        String entity = pick(MODELS);
        double dev = r.nextDouble(-0.6, -0.1);

        return AlertMessage.builder()
                .alertId(UUID.randomUUID().toString())
                .type(t)
                .level(level)
                .title("[" + level.toUpperCase() + "] " + cnName(t) + " - " + entity)
                .content(entity + " 的关键指标偏离阈值 " + String.format("%.1f%%", dev * 100))
                .relatedEntity(entity)
                .triggeredAt(LocalDateTime.now())
                .deviation(dev)
                .build();
    }

    private static String cnName(String type) {
        switch (type) {
            case "profit_anomaly":     return "利润异常";
            case "margin_drop":        return "毛利率下滑";
            case "model_low_contrib":  return "机型贡献偏低";
            case "channel_drop":       return "渠道指标下滑";
            default:                   return type;
        }
    }

    private static <T> T pick(List<T> xs) {
        return xs.get(ThreadLocalRandom.current().nextInt(xs.size()));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
