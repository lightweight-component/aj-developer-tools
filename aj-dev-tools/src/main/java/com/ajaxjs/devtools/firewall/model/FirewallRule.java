package com.ajaxjs.devtools.firewall.model;

import lombok.Getter;

/**
 * 请求匹配使用的防火墙规则，持有加载时计算出的有效配置。
 */
@Getter
public final class FirewallRule {
    private final Long id;
    private final String ruleName;
    private final String apiPattern;
    private final int effectiveQpsLimit;
    private final int effectiveUserLimit;
    private final int effectiveTimeWindow;
    private final boolean effectiveEnabled;

    public FirewallRule(Long id, String ruleName, String apiPattern, Integer qpsLimit,
                        Integer userLimit, Integer timeWindow, Boolean enabled) {
        this.id = id;
        this.ruleName = ruleName;
        this.apiPattern = apiPattern;
        effectiveQpsLimit = positiveOrDefault(qpsLimit, 100);
        effectiveUserLimit = positiveOrDefault(userLimit, 60);
        effectiveTimeWindow = positiveOrDefault(timeWindow, 60);
        effectiveEnabled = enabled == null || enabled;
    }

    private static int positiveOrDefault(Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    /**
     * 检查API路径是否匹配此规则
     *
     * @param apiPath API路径
     * @return 是否匹配
     */
    public boolean matches(String apiPath) {
        if (apiPattern == null || apiPath == null)
            return false;

        // 支持通配符匹配
        String pattern = apiPattern.replace("**", ".*").replace("*", "[^/]*");

        return apiPath.matches(pattern);
    }
}
