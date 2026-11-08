package com.ajaxjs.devtools.firewall.model;

/**
 * 黑白名单共用的精确、通配符和 IPv4 CIDR 匹配逻辑。
 */
final class IpAddressMatcher {
    private IpAddressMatcher() {
    }

    /**
     * 判断地址是否命中规则。
     *
     * @param rule IP 匹配规则
     * @param ip 待检查的地址
     * @param ignoreInvalidCidr 是否将 CIDR 解析异常视为不匹配
     * @return 是否命中
     */
    static boolean matches(String rule, String ip, boolean ignoreInvalidCidr) {
        if (rule == null || ip == null)
            return false;

        if (rule.contains("/")) {
            try {
                return matchesCidr(ip, rule);
            } catch (IllegalArgumentException e) {
                if (ignoreInvalidCidr)
                    return false;

                throw e;
            }
        }

        if (rule.contains("*"))
            return ip.matches(rule.replace("*", ".*"));

        return rule.equals(ip);
    }

    private static boolean matchesCidr(String ip, String cidr) {
        String[] parts = cidr.split("/");
        if (parts.length != 2)
            return false;

        int prefixLength = Integer.parseInt(parts[1]);
        long ipLong = ipToLong(ip);
        long networkLong = ipToLong(parts[0]);
        long mask = (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;

        return (ipLong & mask) == (networkLong & mask);
    }

    private static long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4)
            throw new IllegalArgumentException("Invalid IP address: " + ip);

        long result = 0;
        for (int i = 0; i < 4; i++)
            result = (result << 8) + Integer.parseInt(parts[i]);

        return result;
    }

    /**
     * 组合响应时间等级与调用方格式化后的毫秒值。
     *
     * @param milliseconds 用于分级的原始毫秒值
     * @param displayTime 用于展示的毫秒值
     * @return 带时间数值的等级描述
     */
    static String describe(double milliseconds, String displayTime) {
        String level;

        if (milliseconds < 100)
            level = "快速";
        else if (milliseconds < 500)
            level = "正常";
        else if (milliseconds < 1000)
            level = "较慢";
        else
            level = "缓慢";

        return level + " (" + displayTime + "ms)";
    }
}
