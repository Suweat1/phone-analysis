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
 *   --spring.config.location=file:/home/bigdata/phone-analysis/config/app/application.yml \
 *   > /opt/bigdata/log/app/app.log 2>&amp;1 &amp;
 * </pre>
 */
@SpringBootApplication
@EnableScheduling
public class PhoneAnalysisApp {

    public static void main(String[] args) {
        SpringApplication.run(PhoneAnalysisApp.class, args);
    }
}
