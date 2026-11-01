package com.ajaxjs.devtools.dependencyscanner;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@ComponentScan(basePackages = "com.ajaxjs.devtools.dependencyscanner")
@ConditionalOnProperty(name = "devtools.dependencyscanner.enable", havingValue = "true")
public class DependencyScannerConfiguration {
    /**
     * 配置RestTemplate。
     * <p>默认请求工厂未在此设置连接、读取超时或重试策略。
     *
     * @return 默认配置的 HTTP 客户端
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
