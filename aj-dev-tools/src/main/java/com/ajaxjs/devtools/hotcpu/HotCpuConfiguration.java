package com.ajaxjs.devtools.hotcpu;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.ajaxjs.devtools.hotcpu")
@ConditionalOnProperty(name = "devtools.hotcpu.enabled", havingValue = "true")
public class HotCpuConfiguration {
}
