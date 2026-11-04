package com.example.jarconflict;

import com.example.jarconflict.advisor.ConflictAdvisor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Jar 冲突检测示例应用的 Spring Boot 启动入口。
 * <p>启动 Web 服务并绑定建议配置。扫描由 HTTP 请求触发，启动时不会自动扫描。
 */
@SpringBootApplication
@EnableConfigurationProperties(ConflictAdvisor.class)
public class JarConflictDetectorApplication {
    /**
     * 启动 Spring Boot Web 应用。
     *
     * @param args Spring Boot 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(JarConflictDetectorApplication.class, args);
    }
}