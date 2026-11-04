package com.example.dependencyscanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 依赖漏洞扫描示例应用的 Spring Boot 启动入口。
 * <p>扫描组件并启动 Web 服务。依赖收集和漏洞查询由接口请求同步触发，
 * 启动阶段不自动扫描依赖，也不预加载远程漏洞记录。
 */
@SpringBootApplication
public class DependencyScannerApplication {
    /**
     * 启动 Spring Boot Web 应用，参数交由框架解析。
     *
     * @param args 命令行参数；启动类交给 Spring Boot，正则演示不使用
     */
    public static void main(String[] args) {
        SpringApplication.run(DependencyScannerApplication.class, args);
    }
}