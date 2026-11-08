package com.ajaxjs.devtools.firewall.model;

import lombok.Getter;

/**
 * 缓存中使用的不可变名单条目，封装 IP 匹配和有效性判断。
 */
@Getter
public final class FirewallWhitelist {
    private final Long id;
    private final String ipAddress;
    private final String description;
    private final boolean enabled;

    /**
     * 从持久化字段建立领域对象，未设置启用标志时按启用处理。
     */
    public FirewallWhitelist(Long id, String ipAddress, String description, Boolean enabled) {
        this.id = id;
        this.ipAddress = ipAddress;
        this.description = description;
        this.enabled = enabled == null || enabled;
    }

    /**
     * 检查IP是否有效（启用）
     *
     * @return 是否有效
     */
    public boolean isValid() {
        return enabled;
    }

    /**
     * 检查IP地址是否匹配
     *
     * @param ip 要检查的IP地址
     * @return 是否匹配
     */
    public boolean matches(String ip) {
        return IpAddressMatcher.matches(ipAddress, ip, true);
    }
}
