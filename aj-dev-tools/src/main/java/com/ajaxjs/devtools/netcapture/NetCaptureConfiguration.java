package com.ajaxjs.devtools.netcapture;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;


@Configuration
@ComponentScan(basePackages = "com.ajaxjs.devtools.netcapture")
@ConditionalOnProperty(name = "devtools.network_capture.enable", havingValue = "true")
public class NetCaptureConfiguration {
}
