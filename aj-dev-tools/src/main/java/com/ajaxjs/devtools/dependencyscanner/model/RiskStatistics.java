package com.ajaxjs.devtools.dependencyscanner.model;

/**
 * 按漏洞风险记录汇总的严重程度分布。
 * <p>同一依赖的多个漏洞分别计数。未知或空级别只进入 totalCount，
 * 不进入四个级别的分项，因此分项之和可能小于总数。
 */
public class RiskStatistics {
    /**
     * 全部风险记录数，包括空或未知严重程度。
     */
    public int totalCount = 0;

    /**
     * CRITICAL 风险记录数。
     */
    public int criticalCount = 0;

    /**
     * HIGH 风险记录数。
     */
    public int highCount = 0;

    /**
     * MEDIUM 风险记录数。
     */
    public int mediumCount = 0;

    /**
     * LOW 风险记录数。
     */
    public int lowCount = 0;

    /**
     * 生成中文统计摘要，供扫描完成日志展示。
     *
     * @return 包含总数和四个级别计数的中文摘要
     */
    @Override
    public String toString() {
        return String.format("总计: %d, 严重: %d, 高危: %d, 中危: %d, 低危: %d", totalCount, criticalCount, highCount, mediumCount, lowCount);
    }
}
