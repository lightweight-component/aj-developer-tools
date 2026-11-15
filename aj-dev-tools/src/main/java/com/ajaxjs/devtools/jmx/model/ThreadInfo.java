package com.ajaxjs.devtools.jmx.model;

import lombok.Data;

/**
 * 线程信息
 */
@Data
public class ThreadInfo {
    private Integer liveThreadCount;

    private Integer livePeakThreadCount;

    private Integer daemonThreadCount;

    private Long totalStartedThreadCount;
}
