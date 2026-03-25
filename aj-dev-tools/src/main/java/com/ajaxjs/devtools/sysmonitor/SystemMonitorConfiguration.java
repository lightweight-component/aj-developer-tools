package com.ajaxjs.devtools.sysmonitor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.ajaxjs.devtools.sysmonitor")
@ConditionalOnProperty(name = "devtools.sysmonitor.enable", havingValue = "true")
public class SystemMonitorConfiguration {
}
