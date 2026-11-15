package com.ajaxjs.devtools.simplesysinfo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;


@Configuration
@ComponentScan(basePackages = "com.ajaxjs.devtools.simplesysinfo")
@ConditionalOnProperty(name = "devtools.simplesysinfo.enable", havingValue = "true")
public class SimpleSystemInfoConfiguration {
}
