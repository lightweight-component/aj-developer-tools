package com.example.netspeed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 带宽限速示例的 Spring Boot 启动入口。
 * <p>启动 Web 服务、MVC 拦截器及测试接口。限速通过响应输出流在写入线程中阻塞等待实现，
 * 不是网卡级流量整形，也不限制请求上传速度。
 */
@SpringBootApplication
public class BandwidthLimitApplication {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args Spring Boot 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(BandwidthLimitApplication.class, args);
    }
}
