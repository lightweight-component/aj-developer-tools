package com.ajaxjs.devtools.firewall.model;

import com.ajaxjs.sqlman.annotation.Table;
import com.ajaxjs.sqlman.annotation.Column;
import com.ajaxjs.sqlman.annotation.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 防火墙访问日志实体类
 */
@Table("firewall_access_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FirewallAccessLog {
    /**
     * 主键ID
     */
    @Column(insertable = false, updatable = false)
    private Long id;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * API路径
     */
    private String apiPath;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * 请求方法
     */
    private String requestMethod;

    /**
     * 响应状态码
     */
    private Integer statusCode;

    /**
     * 拦截原因
     */
    private String blockReason;

    /**
     * 请求时间
     */
    private LocalDateTime requestTime;

    /**
     * 响应时间（毫秒）
     */
    private Long responseTime;

    /**
     * 检查是否被拦截
     *
     * @return 是否被拦截
     */
    @Transient
    public boolean isBlocked() {
        return blockReason != null && !blockReason.trim().isEmpty();
    }

    /**
     * 检查是否成功响应
     *
     * @return 是否成功
     */
    @Transient
    public boolean isSuccess() {
        return statusCode != null && statusCode >= 200 && statusCode < 300;
    }

    /**
     * 获取响应时间描述
     *
     * @return 响应时间描述
     */
    @Transient
    public String getResponseTimeDescription() {
        if (responseTime == null)
            return "未知";

        return IpAddressMatcher.describe(responseTime, Long.toString(responseTime));
    }

    /**
     * 获取状态描述
     *
     * @return 状态描述
     */
    @Transient
    public String getStatusDescription() {
        if (isBlocked())
            return "已拦截: " + blockReason;

        if (statusCode == null)
            return "未知";

        switch (statusCode) {
            case 200:
                return "成功";
            case 400:
                return "请求错误";
            case 401:
                return "未授权";
            case 403:
                return "禁止访问";
            case 404:
                return "未找到";
            case 429:
                return "请求过多";
            case 500:
                return "服务器错误";
            default:
                return "状态码: " + statusCode;
        }
    }
}