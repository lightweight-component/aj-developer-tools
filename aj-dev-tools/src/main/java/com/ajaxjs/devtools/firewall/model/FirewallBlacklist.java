package com.ajaxjs.devtools.firewall.model;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 缓存中使用的不可变名单条目，封装 IP 匹配和有效性判断。
 */
@Getter
public final class FirewallBlacklist {
    private final Long id;
    private final String ipAddress;
    private final String reason;
    private final LocalDateTime expireTime;
    private final boolean enabled;

    /**
     * 从持久化字段建立领域对象，未设置启用标志时按启用处理。
     */
    public FirewallBlacklist(Long id, String ipAddress, String reason, LocalDateTime expireTime, Boolean enabled) {
        this.id = id;
        this.ipAddress = ipAddress;
        this.reason = reason;
        this.expireTime = expireTime;
        this.enabled = enabled == null || enabled;
    }

    /**
     * 检查IP是否已过期
     *
     * @return 是否已过期
     */
    public boolean isExpired() {
        return expireTime != null && LocalDateTime.now().isAfter(expireTime);
    }

    /**
     * 检查IP是否有效（启用且未过期）
     *
     * @return 是否有效
     */
    public boolean isValid() {
        return (enabled) && !isExpired();
    }

    /**
     * 检查IP地址是否匹配
     *
     * @param ip 要检查的IP地址
     * @return 是否匹配
     */
    public boolean matches(String ip) {
        return IpAddressMatcher.matches(ipAddress, ip, false);
    }
}
