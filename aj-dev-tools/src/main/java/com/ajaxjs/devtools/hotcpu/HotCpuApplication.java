//package com.ajaxjs.devtools.hotcpu;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.context.ConfigurableApplicationContext;
//import org.springframework.scheduling.annotation.EnableScheduling;
//
//import java.awt.*;
//import java.net.URI;
//
//@SpringBootApplication
//@EnableScheduling
//@Slf4j
//public class HotCpuApplication {
//    public static void main(String[] args) {
//        System.setProperty("spring.output.ansi.enabled", "always");
//        ConfigurableApplicationContext context = SpringApplication.run(HotCpuApplication.class, args);
//        String port = context.getEnvironment().getProperty("server.port", "8080");
//        String contextPath = context.getEnvironment().getProperty("server.servlet.context-path", "") + "hotcpu.html";
//
//        log.info("🚀 Spring Boot Hot CPU Analyzer started successfully!");
//        log.info("🔥 Flame Graph UI: http://localhost:{}{}", port, contextPath);
//        log.info("📊 API Endpoints:");
//        log.info("   - GET  /api/flamegraph        - 获取火焰图数据");
//        log.info("   - POST /api/sampling/enable   - 启用CPU采样");
//        log.info("   - POST /api/sampling/disable  - 禁用CPU采样");
//        log.info("   - GET  /api/sampling/status   - 查看采样状态");
//        log.info("   - POST /api/sampling/clear    - 清空采样数据");
//        log.info("🧪 Test Endpoints:");
//        log.info("   - GET  /test/cpu-intensive    - CPU密集型任务测试");
//        log.info("   - GET  /test/nested-calls     - 嵌套调用测试");
//        log.info("   - GET  /test/mixed-workload   - 混合工作负载测试");
//
//        // 尝试自动打开浏览器
//        try {
//            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
//                Desktop.getDesktop().browse(new URI("http://localhost:" + port + contextPath + "/"));
//                log.info("🖥️  Browser opened automatically");
//            }
//        } catch (Exception e) {
//            log.debug("Could not open browser automatically: {}", e.getMessage());
//        }
//    }
//}