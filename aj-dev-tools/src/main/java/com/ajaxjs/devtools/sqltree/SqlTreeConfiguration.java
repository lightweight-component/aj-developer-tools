package com.ajaxjs.devtools.sqltree;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;


@Configuration
@ComponentScan(basePackages = "com.ajaxjs.devtools.sqltree")
@ConditionalOnProperty(name = "devtools.sqltree.enable", havingValue = "true")
public class SqlTreeConfiguration {
}
