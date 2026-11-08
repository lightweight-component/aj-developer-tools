package com.ajaxjs.devtools.netspeed;

import com.ajaxjs.devtools.netspeed.annotation.BandwidthLimit;
import com.ajaxjs.devtools.netspeed.annotation.BandwidthUnit;
import com.ajaxjs.devtools.netspeed.annotation.LimitType;
import com.ajaxjs.devtools.netspeed.core.BandwidthLimitManager;
import com.ajaxjs.devtools.netspeed.core.BandwidthLimitStats;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 演示全局、接口、用户及 IP 下载限速的控制器。
 * <p>四个注解下载接口输出五 MiB 重复测试数据，显式从 Helper 获取包装响应。
 * 管理接口操作拦截器内部的管理器；自定义参数接口仅回显参数，并未启用动态限速。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BandwidthLimitController {
    private final BandwidthLimitInterceptor bandwidthLimitInterceptor;

    /**
     * 演示全局共享 200 KiB/s 下载，通过包装响应输出五 MiB 测试数据。
     *
     * @param request  当前 HTTP 请求及共享的请求属性
     * @param response 原始或调用者传入的 HTTP 响应
     * @throws IOException 底层输出或刷新操作失败时抛出
     */
    @BandwidthLimit(unit = BandwidthUnit.KB, type = LimitType.GLOBAL)
    @GetMapping("/download/global")
    public void downloadGlobal(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpServletResponse limitedResponse = BandwidthLimitManager.getLimitedResponse(request, response);
        limitedResponse.setContentType("application/octet-stream");
        limitedResponse.setHeader("Content-Disposition", "attachment; filename=global-limit-test.bin");
        limitedResponse.setHeader("X-Test-Type", "Global Limit");
        generateTestData(limitedResponse, 5 * 1024 * 1024); // 5MB
    }

    /**
     * 演示按接口 URI 共享 500 KiB/s 下载，输出五 MiB 测试数据。
     *
     * @param request  当前 HTTP 请求及共享的请求属性
     * @param response 原始或调用者传入的 HTTP 响应
     * @throws IOException 底层输出或刷新操作失败时抛出
     */
    @BandwidthLimit(value = 500, unit = BandwidthUnit.KB, type = LimitType.API)
    @GetMapping("/download/api")
    public void downloadByApi(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpServletResponse limitedResponse = BandwidthLimitManager.getLimitedResponse(request, response);
        limitedResponse.setContentType("application/octet-stream");
        limitedResponse.setHeader("Content-Disposition", "attachment; filename=api-limit-test.bin");
        limitedResponse.setHeader("X-Test-Type", "API Limit");
        generateTestData(limitedResponse, 5 * 1024 * 1024); // 5MB
    }

    /**
     * 演示用户键共享桶，普通档为 200 KiB/s，VIP 档为 1024 KiB/s。
     * <p>方法参数默认用户只用于响应展示；拦截器直接读原始请求头，未收到用户头时以远端地址为键。
     * 同一用户键的后续请求可改变共享桶速率。
     *
     * @param request  当前 HTTP 请求及共享的请求属性
     * @param userType 响应展示的用户类型，未提供参数默认 free
     * @param userId   响应展示的用户标识，未提供参数默认 anonymous
     * @param response 原始或调用者传入的 HTTP 响应
     * @throws IOException 底层输出或刷新操作失败时抛出
     */
    @BandwidthLimit(unit = BandwidthUnit.KB, type = LimitType.USER, free = 200, vip = 1024)
    @GetMapping("/download/user")
    public void downloadByUser(HttpServletRequest request,
                               @RequestHeader(value = "X-User-Type", defaultValue = "free") String userType,
                               @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
                               HttpServletResponse response) throws IOException {
        HttpServletResponse limitedResponse = BandwidthLimitManager.getLimitedResponse(request, response);
        limitedResponse.setContentType("application/octet-stream");
        limitedResponse.setHeader("Content-Disposition", "attachment; filename=user-limit-test.bin");
        limitedResponse.setHeader("X-Test-Type", "User Limit - " + userType);
        limitedResponse.setHeader("X-User-Type", userType);
        limitedResponse.setHeader("X-User-Id", userId);
        generateTestData(limitedResponse, 5 * 1024 * 1024); // 5MB
    }

    /**
     * 演示按来源 IP 共享 300 KiB/s 下载，输出五 MiB 测试数据。
     *
     * @param request  当前 HTTP 请求及共享的请求属性
     * @param response 原始或调用者传入的 HTTP 响应
     * @throws IOException 底层输出或刷新操作失败时抛出
     */
    @BandwidthLimit(value = 300, unit = BandwidthUnit.KB, type = LimitType.IP)
    @GetMapping("/download/ip")
    public void downloadByIp(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpServletResponse limitedResponse = BandwidthLimitManager.getLimitedResponse(request, response);
        limitedResponse.setContentType("application/octet-stream");
        limitedResponse.setHeader("Content-Disposition", "attachment; filename=ip-limit-test.bin");
        limitedResponse.setHeader("X-Test-Type", "IP Limit");
        generateTestData(limitedResponse, 5 * 1024 * 1024); // 5MB
    }

    /**
     * 校验单位与维度枚举并回显参数为 JSON；本接口没有限速注解，不执行动态限速。
     * <p>枚举文本转大写后匹配，非法值抛出 IllegalArgumentException。
     *
     * @param bandwidth 调试请求的带宽数值，仅回显不执行限速
     * @param unit      带宽单位名称，忽略大小写
     * @param type      限速维度或调试传入的维度文本
     * @param response  原始或调用者传入的 HTTP 响应
     * @throws IOException              底层输出或刷新操作失败时抛出
     * @throws IllegalArgumentException 单位或维度名称无法转换为枚举时抛出
     */
    @GetMapping("/download/custom")
    public void downloadCustom(@RequestParam long bandwidth,
                               @RequestParam(defaultValue = "KB") String unit,
                               @RequestParam(defaultValue = "GLOBAL") String type,
                               HttpServletResponse response) throws IOException {
        BandwidthUnit bandwidthUnit = BandwidthUnit.valueOf(unit.toUpperCase());
        LimitType limitType = LimitType.valueOf(type.toUpperCase());

        response.setContentType("application/json");
        response.setHeader("X-Test-Type", "Custom Limit");
        response.setHeader("X-Bandwidth", bandwidth + " " + unit);
        response.setHeader("X-Limit-Type", type);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Custom bandwidth limit request");
        result.put("bandwidth", bandwidth);
        result.put("unit", unit);
        result.put("type", type);
        result.put("note", "This endpoint shows the parameters. Use the annotated endpoints for actual limiting.");

        response.getWriter().write(toJson(result));
    }

    /**
     * 读取拦截器统计并转换为页面展示文本，包括速率后缀和百分比。
     * <p>拦截器未注入时返回 error；没有全局桶时管理器返回全零快照。
     *
     * @return 展示用统计或错误说明
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();

        if (bandwidthLimitInterceptor != null) {
            BandwidthLimitStats limitStats = bandwidthLimitInterceptor.getStats();
            stats.put("globalCapacity", BandwidthUnit.formatBytes(limitStats.getGlobalCapacity()));
            stats.put("globalRefillRate", BandwidthUnit.formatBytes(limitStats.getGlobalRefillRate()) + "/s");
            stats.put("globalAvailableTokens", BandwidthUnit.formatBytes(limitStats.getGlobalAvailableTokens()));
            stats.put("globalBytesConsumed", BandwidthUnit.formatBytes(limitStats.getGlobalBytesConsumed()));
            stats.put("globalActualRate", BandwidthUnit.formatBytes((long) limitStats.getGlobalActualRate()) + "/s");
            stats.put("globalUtilization", String.format("%.1f%%", limitStats.getGlobalUtilization() * 100));
            stats.put("apiBucketCount", limitStats.getApiBucketCount());
            stats.put("userBucketCount", limitStats.getUserBucketCount());
            stats.put("ipBucketCount", limitStats.getIpBucketCount());
        } else
            stats.put("error", "BandwidthLimitInterceptor not available");

        return stats;
    }

    /**
     * 委托拦截器恢复全局桶初始配置，不清除累计统计。
     *
     * @return 包含 status 和 message 的结果映射
     */
    @PostMapping("/reset/global")
    public Map<String, Object> resetGlobal() {
        Map<String, Object> result = new HashMap<>();

        if (bandwidthLimitInterceptor != null) {
            bandwidthLimitInterceptor.resetGlobalBucket();
            result.put("status", "success");
            result.put("message", "Global bandwidth limit bucket reset");
        } else {
            result.put("status", "error");
            result.put("message", "BandwidthLimitInterceptor not available");
        }

        return result;
    }

    /**
     * 清除 API、USER、IP 桶映射，不清除全局桶；已在传输的流继续持有旧桶引用。
     *
     * @return 包含 status 和 message 的结果映射
     */
    @PostMapping("/reset/all")
    public Map<String, Object> resetAll() {
        Map<String, Object> result = new HashMap<>();

        if (bandwidthLimitInterceptor != null) {
            bandwidthLimitInterceptor.clearAllBuckets();
            result.put("status", "success");
            result.put("message", "All bandwidth limit buckets cleared");
        } else {
            result.put("status", "error");
            result.put("message", "BandwidthLimitInterceptor not available");
        }

        return result;
    }

    /**
     * 设置 Content-Length 并以八 KiB 缓冲循环写出指定字节量的固定重复模式。
     * <p>最后刷新流，不在本方法关闭输出流。写入是否限速取决于传入响应。
     *
     * @param response 原始或调用者传入的 HTTP 响应
     * @param size     要写出的测试字节数
     * @throws IOException 底层输出或刷新操作失败时抛出
     */
    private void generateTestData(HttpServletResponse response, int size) throws IOException {
        response.setContentLengthLong(size);

        byte[] buffer = new byte[8192];
        byte[] pattern = "This is a bandwidth limit test data. ".getBytes(StandardCharsets.UTF_8);

        int patternPos = 0;
        int remaining = size;

        while (remaining > 0) {
            int chunkSize = Math.min(buffer.length, remaining);

            for (int i = 0; i < chunkSize; i++) {
                buffer[i] = pattern[patternPos];
                patternPos = (patternPos + 1) % pattern.length;
            }

            response.getOutputStream().write(buffer, 0, chunkSize);
            remaining -= chunkSize;
        }

        response.getOutputStream().flush();
    }

    /**
     * 将 Map 拼接为简单 JSON 文本，数字直接输出，其他值用引号包围。
     * <p>未转义引号、反斜杠或控制字符，也不完整处理布尔、null、嵌套对象，仅用于示例回显。
     *
     * @param map 用于示例回显的字段映射
     * @return 拼接后的文本，不保证对任意 Map 均为有效 JSON
     */
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first)
                sb.append(",");

            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();

            if (value instanceof String)
                sb.append("\"").append(value).append("\"");
            else if (value instanceof Number)
                sb.append(value);
            else
                sb.append("\"").append(value).append("\"");

            first = false;
        }

        sb.append("}");

        return sb.toString();
    }
}
