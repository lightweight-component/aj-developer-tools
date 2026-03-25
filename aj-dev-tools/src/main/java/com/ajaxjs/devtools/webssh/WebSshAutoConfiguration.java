package com.ajaxjs.devtools.webssh;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.ajaxjs.devtools.webssh")
@ConditionalOnProperty(name = "devtools.webssh.enabled", havingValue = "true")
public class WebSshAutoConfiguration {
}
