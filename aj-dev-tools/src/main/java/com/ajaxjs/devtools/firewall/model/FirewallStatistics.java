package com.ajaxjs.devtools.firewall.model;

import com.ajaxjs.sqlman.annotation.Table;
import com.ajaxjs.sqlman.annotation.Column;
import com.ajaxjs.sqlman.annotation.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 防火墙统计实体类
 *
 * @author Firewall Team
 * @version 1.0.0
 */
@Table("firewall_statistics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FirewallStatistics {
    /**
     * 主键ID
     */
    @Column(insertable = false, updatable = false)
    private Long id;

    /**
     * 统计日期
     */
    @Column(updatable = false)
    private LocalDate statDate;

    /**
     * API路径
     */
    @Column(updatable = false)
    private String apiPath;

    /**
     * 总请求数
     */
    private Long totalRequests;

    /**
     * 被拦截请求数
     */
    private Long blockedRequests;

    /**
     * 平均响应时间
     */
    private BigDecimal avgResponseTime;

    /**
     * 创建时间
     */
    @Column(insertable = false, updatable = false)
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @Column(insertable = false)
    private LocalDateTime updatedTime;

    /**
     * 计算拦截率
     *
     * @return 拦截率（百分比）
     */
    @Transient
    public double getBlockRate() {
        if (totalRequests == null || totalRequests == 0)
            return 0.0;

        long blocked = blockedRequests != null ? blockedRequests : 0;

        return (double) blocked / totalRequests * 100;
    }

    /**
     * 计算成功率
     *
     * @return 成功率（百分比）
     */
    @Transient
    public double getSuccessRate() {
        return 100.0 - getBlockRate();
    }

    /**
     * 获取拦截率描述
     *
     * @return 拦截率描述
     */
    @Transient
    public String getBlockRateDescription() {
        double rate = getBlockRate();

        if (rate == 0)
            return "无拦截";
        else if (rate < 1)
            return "极低 (" + String.format("%.2f", rate) + "%)";
        else if (rate < 5)
            return "较低 (" + String.format("%.2f", rate) + "%)";
        else if (rate < 20)
            return "中等 (" + String.format("%.2f", rate) + "%)";
        else
            return "较高 (" + String.format("%.2f", rate) + "%)";
    }

    /**
     * 获取响应时间描述
     *
     * @return 响应时间描述
     */
    @Transient
    public String getResponseTimeDescription() {
        if (avgResponseTime == null)
            return "未知";

        double time = avgResponseTime.doubleValue();

        return IpAddressMatcher.describe(time, String.format("%.1f", time));
    }

    /**
     * 增加请求统计
     *
     * @param isBlocked    是否被拦截
     * @param responseTime 响应时间
     */
    public void addRequest(boolean isBlocked, long responseTime) {
        totalRequests = (totalRequests != null ? totalRequests : 0) + 1;// 增加总请求数

        if (isBlocked) // 增加拦截数
            blockedRequests = (blockedRequests != null ? this.blockedRequests : 0) + 1;

        if (avgResponseTime == null)// 更新平均响应时间
            avgResponseTime = BigDecimal.valueOf(responseTime);
        else {
            // 计算新的平均值
            BigDecimal currentTotal = avgResponseTime.multiply(BigDecimal.valueOf(totalRequests - 1));
            BigDecimal newTotal = currentTotal.add(BigDecimal.valueOf(responseTime));
            avgResponseTime = newTotal.divide(BigDecimal.valueOf(totalRequests), 2, BigDecimal.ROUND_HALF_UP);
        }

        updatedTime = LocalDateTime.now();// 更新时间
    }
}