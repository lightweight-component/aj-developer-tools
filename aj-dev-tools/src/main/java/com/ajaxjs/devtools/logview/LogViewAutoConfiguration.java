package com.ajaxjs.devtools.logview;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Data
@Configuration
@ComponentScan(basePackages = "com.ajaxjs.devtools.logview")
@ConfigurationProperties(prefix = "devtools.logviewer")
@ConditionalOnProperty(name = "devtools.logview.enabled", havingValue = "true")
public class LogViewAutoConfiguration {
    /**
     * 日志文件根目录
     */
    private String logPath = "./logs";

    /**
     * 允许访问的日志文件扩展名
     */
    private List<String> allowedExtensions = Arrays.asList(".log", ".txt");

    /**
     * 单次查询最大行数
     */
    private int maxLines = 1000;

    /**
     * 文件最大大小（MB）
     */
    private long maxFileSize = 100;

    /**
     * 是否启用安全检查
     */
    private boolean enableSecurity = true;
}
