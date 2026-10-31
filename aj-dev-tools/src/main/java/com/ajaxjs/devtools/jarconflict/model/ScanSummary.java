package com.ajaxjs.devtools.jarconflict.model;

import lombok.Data;

/**
 * 扫描结果的聚合统计。
 * <p>总类数量为各依赖类清单长度之和，重复类重复计数；冲突数量是记录数，
 * 并非涉及的 JAR 数量。严重程度分布使用建议生成后的最终级别。
 */
@Data
public class ScanSummary {
    /**
     * 扫描条目总数，可能包含开发环境目录。
     */
    private int totalJars;

    /**
     * 全部类清单长度之和，不去重。
     */
    private int totalClasses;

    /**
     * 冲突记录总数。
     */
    private int conflictCount;
    /**
     * 最终严重程度为 CRITICAL 的记录数。
     */
    private int criticalConflicts;

    /**
     * 最终严重程度为 HIGH 的记录数。
     */
    private int highConflicts;

    /**
     * 最终严重程度为 MEDIUM 的记录数。
     */
    private int mediumConflicts;

    /**
     * 最终严重程度为 LOW 的记录数。
     */
    private int lowConflicts;
}