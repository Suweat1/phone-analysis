package com.phone.app.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.phone.app.mapper")
public class MybatisConfig {
}
