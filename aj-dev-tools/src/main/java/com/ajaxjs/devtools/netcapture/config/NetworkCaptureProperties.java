package com.ajaxjs.devtools.netcapture.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "devtools.netcapture.capture")
public class NetworkCaptureProperties {
    private String networkInterface = "";

    private String filter = "";

    private int bufferSize = 65536;

    private int timeout = 1000;

    private boolean promiscuous = false;

    private int maxPackets = 0;

    private boolean autoStart = false;

    private int dataRetentionHours = 24;
}