package com.ajaxjs.devtools.memviz;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 内存可视化组件配置，扫描本包内的控制器与解析服务。
 * <p>仅在 {@code devtools.memviz.enable=true} 时生效；未配置时不通过此配置启用组件。</p>
 */
@Configuration
@ComponentScan(basePackages = "com.ajaxjs.devtools.memviz")
@ConditionalOnProperty(name = "devtools.memviz.enable", havingValue = "true")
public class MemvizConfiguration {
}
