package com.phone.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Web MVC 总配置：
 * <ol>
 *   <li><b>CORS</b>：开发期全开（保留原有逻辑）。</li>
 *   <li><b>REST 路径前缀</b>：把所有 {@link RestController} 注解的 Bean 自动挂到
 *       {@code /api} 之下；Controller 源码里继续写 {@code /dashboard/...} 即可，
 *       生效路径是 {@code /api/dashboard/...}。<br>
 *       这种做法替代了原先的 {@code server.servlet.context-path=/api} 全局前缀
 *       —— context-path 会把静态资源（{@code /index.html}）也挪进 {@code /api/}
 *       下，不利于把 Vue dist 直接托管在根路径。</li>
 *   <li><b>SPA fallback</b>：把根路径请求映射到 Vue 的 {@code index.html}。
 *       Vue Router 当前用 hash 模式不依赖此 fallback，但保留下来对未来切
 *       history 模式有用。</li>
 * </ol>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 由 PathMatchConfigurer 自动注入的 REST 前缀 */
    public static final String API_PREFIX = "/api";

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 给所有 @RestController 的 mapping 加上 /api 前缀。
     *
     * Spring 按 (Class -> boolean) 谓词决定要不要前缀；这里用 RestController 注解判定，
     * 覆盖整张业务 Controller 表。原 Controller 类里的 @RequestMapping 保持原样不动。
     *
     * 注意：SPA 兜底必须用 @Controller（非 @RestController），否则会被一并挪到 /api。
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX,
                c -> c.isAnnotationPresent(RestController.class));
    }

    /**
     * SPA 兜底：把根路径与 index.html 都返回 Vue 编译后的 index.html。
     *
     * - 用 @Controller + @ResponseBody（不是 @RestController）刻意避开
     *   {@link #configurePathMatch(PathMatchConfigurer)} 给 @RestController 的 /api 前缀；
     * - Spring 内置 ResourceHttpRequestHandler 会优先匹配静态资源（css/js/img），
     *   所以这里只在 URI 不对应任何静态文件时才会被命中；
     * - 没必要写 /** —— Vue Router 当前用 hash 模式（#/foo），所有页面都在根路径下。
     *   切到 history 模式再扩展这里的 GetMapping 即可。
     */
    @Controller
    public static class SpaFallbackController {

        @GetMapping(value = {"/", "/index.html"}, produces = MediaType.TEXT_HTML_VALUE)
        @ResponseBody
        public ResponseEntity<String> indexHtml() throws Exception {
            Resource res = new ClassPathResource("static/index.html");
            if (!res.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("Vue 前端未构建：请先运行 scripts/build-all.sh 或 npm run build，"
                                + "并确认 app/src/main/resources/static/ 下存在 index.html。");
            }
            // 读出 UTF-8，避免中文标题乱码
            String body;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
                body = br.lines().collect(Collectors.joining("\n"));
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")  // index.html 不缓存，hash 命中后续 css/js
                    .body(body);
        }
    }
}

