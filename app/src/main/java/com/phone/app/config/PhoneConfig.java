package com.phone.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 业务参数宏变量入口（与 application.yml 的 {@code phone.*} 段一一对应）。
 *
 * <p>本类是「<b>配置 + 宏变量铁律</b>」在 app 模块的唯一落点：
 * <ul>
 *   <li>所有业务代码 <b>只</b> 通过 {@code @Autowired PhoneConfig} 拿连接信息 / topic / 阈值；</li>
 *   <li><b>禁止</b> 在 Controller/Service/Mapper/Listener 中硬编码 URL、密码、路径、topic 名；</li>
 *   <li>修改这些值只动 {@code config/app/application.yml}，无需重编。</li>
 * </ul>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "phone")
public class PhoneConfig {

    private Hive hive = new Hive();
    private Hdfs hdfs = new Hdfs();
    private Kafka kafka = new Kafka();
    private Redis redis = new Redis();
    private Dashboard dashboard = new Dashboard();
    private Simulator simulator = new Simulator();

    @Data
    public static class Hive {
        private String jdbcUrl;
        private String driver;
        private String username;
        private String password;
        private int poolSize = 5;
    }

    @Data
    public static class Hdfs {
        private String namenode;
        private String rawDir;
        private String adsDir;
    }

    @Data
    public static class Kafka {
        private String topicRaw;
        private String topicMetric;
        private String topicAlert;
    }

    @Data
    public static class Redis {
        private String keyPrefix = "phone:";
        private long dashboardTtlSeconds = 300L;
        private long alertTtlSeconds = 86400L;
    }

    @Data
    public static class Dashboard {
        private double profitAnomalyThreshold = 0.3;
        private int lowContribTopN = 10;
        private int highValueTopN = 10;
        private long refreshIntervalSeconds = 600L;
    }

    @Data
    public static class Simulator {
        private boolean enabled = true;
        private long intervalMs = 5000L;
        private int batchSize = 20;
        private String sourceParquet;
    }
}
