package com.example.netspeed.config;

import com.example.netspeed.manager.BandwidthLimitManager;
import com.example.netspeed.web.BandwidthLimitInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册示例的限速拦截器及独立管理器 Bean。
 * <p>拦截器应用于 /api/**，实际是否创建包装响应取决于限速注解。
 * 拦截器内部自行创建管理器，与本配置另外暴露的管理器 Bean 并非同一实例，统计及桶状态相互独立。
 */
@Configuration
public class BandwidthLimitConfig implements WebMvcConfigurer {
    /**
     * 配置中缓存的拦截器或控制器可选注入的拦截器实例。
     */
    private BandwidthLimitInterceptor bandwidthLimitInterceptor;

    /**
     * 懒创建并缓存拦截器，作为 Spring Bean 返回。
     *
     * @return 本配置持有的拦截器实例
     */
    @Bean
    public BandwidthLimitInterceptor bandwidthLimitInterceptor() {
        if (bandwidthLimitInterceptor == null)
            bandwidthLimitInterceptor = new BandwidthLimitInterceptor();

        return bandwidthLimitInterceptor;
    }

    /**
     * 实现 WebMvcConfigurer，将限速拦截器注册到 /api/**。
     * <p>没有匹配限速注解的请求仍直接放行。
     *
     * @param registry MVC 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(bandwidthLimitInterceptor()).addPathPatterns("/api/**");
    }

    /**
     * 创建独立的管理器 Bean，其构造会启动守护清理线程。
     * <p>该实例未注入拦截器，与拦截器自己的管理器不是同一对象。
     *
     * @return 新的管理器实例
     */
    @Bean
    public BandwidthLimitManager bandwidthLimitManager() {
        return new BandwidthLimitManager();
    }
}
