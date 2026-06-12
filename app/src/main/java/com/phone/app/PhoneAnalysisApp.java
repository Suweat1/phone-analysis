package com.phone.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * phone-analysis 应用层入口。
 *
 * <p>启动方式（运行机）：
 * <pre>
 * nohup java -jar phone-analysis-app.jar \
 *   &gt; /opt/bigdata/log/app/app.log 2&gt;&amp;1 &amp;
 * </pre>
 *
 * <p>application.yml 已位于 jar 的 classpath（{@code app/src/main/resources/application.yml}）。
 * 若运行环境需要覆盖部分字段（如改密码/主机名），追加：
 * <pre>
 *   --spring.config.additional-location=file:${HOME}/phone-analysis/config/app/application.yml
 * </pre>
 * 后者优先级更高，逐字段覆盖 classpath 默认值。
 */
@SpringBootApplication
@EnableScheduling
public class PhoneAnalysisApp {

    public static void main(String[] args) {
        SpringApplication.run(PhoneAnalysisApp.class, args);
    }
}
