package com.ajaxjs.dbhelper;

import com.ajaxjs.dataservice.EndpointMgr;
import com.ajaxjs.dataservice.model.ActionType;
import com.ajaxjs.dataservice.model.Endpoint;
import com.ajaxjs.dataservice.model.Group;
import com.ajaxjs.framework.cache.Cache;
import com.ajaxjs.framework.cache.lru.LRUCache;
import com.ajaxjs.iam.client.CacheProvider;
import com.ajaxjs.util.ObjectHelper;
import com.ajaxjs.util.httpremote.call.CallHandler;
import com.ajaxjs.util.httpremote.model.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@EnableAsync
public class AppConfigure implements WebMvcConfigurer {
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(60000); // 60秒
    }

    @Bean
    Cache<String, Object> initLocalCache() {
        return new LRUCache<>(500);
    }

    @Bean
    CacheProvider initCacheProvider() {
        Cache<String, Object> cache = initLocalCache();
        return new CacheProvider() {
            @Override
            public void save(String key, String value, int expireSeconds) {
                cache.put(key, value, expireSeconds);
            }

            @Override
            public String get(String key) {
                return cache.get(key, String.class);
            }

            @Override
            public void remove(String key) {
                cache.remove(key);
            }
        };
    }

    @Bean
    EndpointMgr initEndpointMgr() {
        Group group = new Group();
        group.setId(1);
        group.setUrl("/foo");

        List<Group> groups = ObjectHelper.listOf(group);

        Endpoint endpoint = new Endpoint();
        endpoint.setId(1);
        endpoint.setGroupId(1);
        endpoint.setUrl("/bar");
        endpoint.setActionType(ActionType.INFO);
        endpoint.setSql("select * from shop_address where id = #{id}");
        endpoint.setMethod(HttpMethod.GET);

        List<Endpoint> endpoints = ObjectHelper.listOf(endpoint);

        return EndpointMgr.init(groups, endpoints);
    }
}
