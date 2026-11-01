package com.ajaxjs.devtools.apidashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * API 面板的条件配置入口，注册 MVC 请求拦截器并启用定时清理。
 * <p>仅当 {@code devtools.apidashboard.enable=true} 时生效，默认不启用。
 * 组件扫描覆盖本包；Lombok 生成构造器注入拦截器。启用调度后，本上下文中的
 * 其他 {@code @Scheduled} 方法也会由 Spring 调度基础设施处理。</p>
 */
@Configuration
@ComponentScan(basePackages = "com.ajaxjs.devtools.apidashboard")
@ConditionalOnProperty(name = "devtools.apidashboard.enable", havingValue = "true")
@EnableScheduling
@RequiredArgsConstructor
public class ApiDashboardConfiguration implements WebMvcConfigurer {
    /**
     * 用于 MVC 请求计时的共享拦截器，通过构造器注入。
     */
    private final MetricsInterceptor metricsInterceptor;

    /**
     * 向 MVC 注册全部路径的指标拦截器，并显式排除 /error。
     * <p>其他静态资源及指标控制器由拦截器判断后跳过；无需为业务控制器增加注解。</p>
     *
     * @param registry MVC 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(metricsInterceptor).addPathPatterns("/**").excludePathPatterns("/error");
    }
}
