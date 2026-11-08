package com.ajaxjs.devtools.firewall.model;

import com.ajaxjs.sqlman.annotation.Table;
import com.ajaxjs.sqlman.annotation.Column;
import com.ajaxjs.sqlman.annotation.Transient;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 防火墙规则表记录，保留数据库中的原始配置值。
 */
@Table("firewall_rule")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FirewallRuleRow {
    /**
     * 主键ID
     */
    @Column(insertable = false, updatable = false)
    private Long id;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * API路径匹配模式
     */
    private String apiPattern;

    /**
     * QPS限制（每秒最大请求数）
     */
    private Integer qpsLimit;

    /**
     * 单用户时间窗限制（分钟内最大请求数）
     */
    private Integer userLimit;

    /**
     * 时间窗口（秒）
     */
    private Integer timeWindow;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 规则描述
     */
    private String description;

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
}
