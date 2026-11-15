package com.ajaxjs.devtools.jmx;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;


@Configuration
@ComponentScan(basePackages = "com.ajaxjs.devtools.jmx")
@ConditionalOnProperty(name = "devtools.jmx.enable", havingValue = "true")
public class JmxConfiguration {
}
