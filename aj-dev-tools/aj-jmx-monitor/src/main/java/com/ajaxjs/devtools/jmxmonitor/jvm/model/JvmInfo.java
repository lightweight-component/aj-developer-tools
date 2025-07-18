package com.ajaxjs.devtools.jmxmonitor.jvm.model;

import lombok.Data;

import java.util.List;
import java.util.TreeMap;

/**
 * Jvm 信息
 */
@Data
public class JvmInfo {
    private String name;

    private String classPath;

    private Long startTime;

    private String specName;

    private String specVendor;

    private String specVersion;

    private String managementSpecVersion;

    private String[] inputArguments;

    /**
     * 67 个系统参数
     */
    private List<TreeMap<String, Object>> systemProperties;

    private String vmName;

    private String vmVendor;

    private String vmVersion;

    private String libraryPath;

    private Boolean bootClassPathSupported;

    private String bootClassPath;

    private Long uptime;
}
