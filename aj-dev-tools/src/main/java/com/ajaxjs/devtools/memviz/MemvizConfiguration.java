package com.ajaxjs.devtools.memviz;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.ajaxjs.devtools.memviz")
@ConditionalOnProperty(name = "devtools.memviz.enable", havingValue = "true")
public class MemvizConfiguration {
}
