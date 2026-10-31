package com.ajaxjs.devtools.jarconflict.model;

/**
 * 风险级别，声明顺序由低到高，供页面统计和规则配置使用。
 */
public enum SeverityLevel {
    /**
     * 低风险。
     */
    LOW,

    /**
     * 中等风险。
     */
    MEDIUM,

    /**
     * 高风险。
     */
    HIGH,

    /**
     * 严重风险。
     */
    CRITICAL
}