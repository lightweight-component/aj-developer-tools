package com.ajaxjs.devtools_app.common;

import com.ajaxjs.dataservice.EndpointMgr;
import com.ajaxjs.dataservice.fastcrud.Namespaces;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Config {
    @Bean
    Namespaces initNamespaces() {
        return new Namespaces();
    }

    @Bean
    EndpointMgr initEndpointMgr() {
        return new EndpointMgr(1);
    }

}
