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
 * 防火墙黑名单表记录，保留数据库原始字段值。
 */
@Table("firewall_blacklist")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FirewallBlacklistRow {
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
     * 封禁原因
     */
    private String reason;

    /**
     * 过期时间（NULL表示永久）
     */
    private LocalDateTime expireTime;

    /**
     * 是否启用
     */
    private Boolean enabled;

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
