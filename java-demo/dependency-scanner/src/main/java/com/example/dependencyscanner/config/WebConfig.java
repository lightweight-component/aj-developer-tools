package com.example.dependencyscanner.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestTemplate;

/**
 * 提供在线查询使用的 HTTP 客户端及 JSON 映射器，并启用事务管理。
 * <p>两个 Bean 均使用默认构造配置，没有额外配置网络超时、请求重试或 Jackson 模块。
 * 并行扫描中的 Future 等待超时不会替代 HTTP 客户端的连接和读取超时。
 */
@Configuration
@EnableTransactionManagement
public class WebConfig {
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

    /**
     * 配置ObjectMapper。
     * <p>使用默认 ObjectMapper，不额外注册模块或命名策略。
     *
     * @return 默认配置的 JSON 映射器
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}