package com.ajaxjs.devtools.jarconflict.model;

/**
 * 检测器支持的三种潜在冲突。
 */
public enum ConflictType {
    /**
     * 同一类名出现在多个依赖清单中。
     */
    CLASS_DUPLICATE,

    /**
     * 同一库名对应多个已知版本。
     */
    VERSION_CONFLICT,

    /**
     * 多个依赖具有相同的启发式 JAR 签名。
     */
    JAR_DUPLICATE
}