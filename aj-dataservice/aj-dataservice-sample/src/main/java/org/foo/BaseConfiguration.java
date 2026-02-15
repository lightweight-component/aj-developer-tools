package org.foo;

import com.ajaxjs.framework.database.DataBaseConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.sql.DataSource;

/**
 * 程序配置
 */
@Configuration
public class BaseConfiguration implements WebMvcConfigurer {
    @Value("${auth.excludes: }")
    private String excludes;

    /**
     * 加入认证拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
//        InterceptorRegistration interceptorRegistration = registry.addInterceptor(authInterceptor());
//        interceptorRegistration.addPathPatterns("/**"); // 拦截所有
//
//        // 不需要的拦截路径
//        if (StringUtils.hasText(excludes)) {
//            String[] arr = excludes.split("\\|");
//            interceptorRegistration.excludePathPatterns(arr);
//        }
    }

    /**
     * 用户全局拦截器
     */
//    @Bean
//    UserInterceptor authInterceptor() {
//        return new UserInterceptor();
//    }

//    @Bean
//    @Qualifier("DS_beforeCreate")
//    public Consumer<Map<String, Object>> beforeCreate() {
//        return Version.isDebug ? BaseCrudPlugins.beforeCreate : DisableWritePlugins.beforeCreate;
//    }
//
//    @Bean
//    @Qualifier("DS_beforeUpdate")
//    public Consumer<Map<String, Object>> beforeUpdate() {
//        return Version.isDebug ? BaseCrudPlugins.beforeUpdate : DisableWritePlugins.beforeUpdate;
//    }
//
//    @Bean
//    @Qualifier("DS_beforeDelete")
//    public BiFunction<Boolean, String, String> beforeDelete() {
//        return Version.isDebug ? BaseCrudPlugins.beforeDelete : DisableWritePlugins.beforeDelete;
//    }
}