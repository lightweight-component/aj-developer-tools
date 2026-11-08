package com.ajaxjs.devtools.netspeed.core;

import com.ajaxjs.devtools.netspeed.BandwidthLimitResponseWrapper;
import com.ajaxjs.devtools.netspeed.annotation.LimitType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 在单个实例内管理全局、API、用户及 IP 维度的令牌桶。
 * <p>全局桶同步创建，其他桶通过 ConcurrentHashMap.compute 创建或复用。
 * 复用时只调整填充速率，不改变已有容量；相同维度和键的不同策略会共享并覆盖速率。
 * <p>每分钟清理超过五分钟未访问的非全局桶。访问时间映射未包含维度，
 * 且首次创建桶未写入访问时间，因此新桶可能在首次清理时被移除。
 */
@Slf4j
public class BandwidthLimitManager {
    /**
     * 管理器实例内共享的全局桶，首次使用前为 null。
     */
    private TokenBucket globalBucket;

    /**
     * 以请求 URI 为键的接口共享桶集合。
     */
    private final ConcurrentHashMap<String, TokenBucket> apiBuckets = new ConcurrentHashMap<>();

    /**
     * 以用户标识或回退地址为键的用户共享桶集合。
     */
    private final ConcurrentHashMap<String, TokenBucket> userBuckets = new ConcurrentHashMap<>();

    /**
     * 以提取的来源 IP 为键的共享桶集合。
     */
    private final ConcurrentHashMap<String, TokenBucket> ipBuckets = new ConcurrentHashMap<>();

    /**
     * 执行定时空闲清理的单线程守护执行器。
     */
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "bandwidth-limit-cleanup");
        thread.setDaemon(true);

        return thread;
    });

    /**
     * 裸键到最后复用毫秒时间的映射，未包含维度，首次创建时未记录。
     */
    private final ConcurrentHashMap<String, Long> lastAccessTime = new ConcurrentHashMap<>();

    /**
     * 非全局桶的空闲阈值，五分钟，单位毫秒。
     */
    private static final long IDLE_TIMEOUT_MS = 5 * 60 * 1000;

    /**
     * 创建管理器并启动每分钟执行的守护清理任务，首次延迟一分钟。
     */
    public BandwidthLimitManager() {
        startCleanupTask();// 启动定时清理任务
    }

    /**
     * 按限速维度选择桶集合，获取或创建共享桶。
     * <p>GLOBAL 忽略 key；其他维度按键复用，已有桶只更新速率不更新容量。
     *
     * @param type       限速维度或调试传入的维度文本
     * @param key        维度内共享桶键，非全局维度不应为 null
     * @param capacity   桶容量，单位为字节，应为正数
     * @param refillRate 填充速率，单位为字节/秒，应为正数
     * @return 对应实例内共享的令牌桶
     */
    public TokenBucket getBucket(LimitType type, String key, long capacity, long refillRate) {
        return switch (type) {
            case GLOBAL -> getGlobalBucket(capacity, refillRate);
            case API -> getOrCreateBucket(apiBuckets, key, capacity, refillRate);
            case USER -> getOrCreateBucket(userBuckets, key, capacity, refillRate);
            case IP -> getOrCreateBucket(ipBuckets, key, capacity, refillRate);
        };
    }

    /**
     * 同步创建或复用全局桶，仅在速率改变时更新速率，容量保留首次创建值。
     *
     * @param capacity   桶容量，单位为字节，应为正数
     * @param refillRate 填充速率，单位为字节/秒，应为正数
     * @return 全局桶实例
     */
    private synchronized TokenBucket getGlobalBucket(long capacity, long refillRate) {
        if (globalBucket == null) {
            globalBucket = new TokenBucket(capacity, refillRate);
            log.info("Created global bandwidth limit bucket: capacity={}, rate={}/s", capacity, formatBytes(refillRate));
        } else if (globalBucket.getRefillRate() != refillRate) {
            globalBucket.setRefillRate(refillRate);  // 动态调整速率
            log.info("Updated global bandwidth limit rate: {}/s", formatBytes(refillRate));
        }

        return globalBucket;
    }

    /**
     * 使用 compute 原子地创建或复用指定维度的桶。
     * <p>首次创建直接返回，未写入最后访问时间；只有复用时更新时间。
     * 时间映射以裸 key 为键，不区分 API、USER、IP。
     *
     * @param buckets    指定维度的可变桶映射
     * @param key        维度内共享桶键，非全局维度不应为 null
     * @param capacity   桶容量，单位为字节，应为正数
     * @param refillRate 填充速率，单位为字节/秒，应为正数
     * @return 指定键关联的令牌桶
     */
    private TokenBucket getOrCreateBucket(ConcurrentHashMap<String, TokenBucket> buckets, String key, long capacity, long refillRate) {
        return buckets.compute(key, (k, existing) -> {
            if (existing == null) {
                log.debug("Created new bandwidth limit bucket for {}: capacity={}, rate={}/s", k, capacity, formatBytes(refillRate));

                return new TokenBucket(capacity, refillRate);
            }

            lastAccessTime.put(key, System.currentTimeMillis()); // 更新最后访问时间

            if (existing.getRefillRate() != refillRate) { // 动态调整速率
                existing.setRefillRate(refillRate);
                log.debug("Updated bandwidth limit rate for {}: {}/s", k, formatBytes(refillRate));
            }

            return existing;
        });
    }

    /**
     * 安排每分钟清理任务，捕获任务异常以保留后续定时执行。
     */
    private void startCleanupTask() {
        cleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                cleanupIdleBuckets();
            } catch (Exception e) {
                log.error("Error during cleanup", e);
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * 清理三个非全局集合及过期时间记录，不清理全局桶。
     * <p>使用当前毫秒时间，访问记录缺失也视为可移除。
     */
    private void cleanupIdleBuckets() {
        long now = System.currentTimeMillis();

        cleanupMap(apiBuckets, now, "API"); // 清理 API 维度
        cleanupMap(userBuckets, now, "USER");   // 清理用户维度
        cleanupMap(ipBuckets, now, "IP");   // 清理 IP 维度

        lastAccessTime.entrySet().removeIf(entry -> now - entry.getValue() > IDLE_TIMEOUT_MS); // 清理访问时间记录
    }

    /**
     * 移除最后访问时间缺失或距今超过五分钟的桶，同时移除时间记录。
     * <p>移除映射不使已持有桶引用的流失效，跨集合同键共用时间记录可能互相影响。
     *
     * @param buckets 指定维度的可变桶映射
     * @param now     当前毫秒时间戳
     * @param type    限速维度或调试传入的维度文本
     */
    private void cleanupMap(ConcurrentHashMap<String, TokenBucket> buckets, long now, String type) {
        buckets.keySet().removeIf(key -> {
            Long lastAccess = lastAccessTime.get(key);

            if (lastAccess == null || now - lastAccess > IDLE_TIMEOUT_MS) {
                log.debug("Removed idle {} bandwidth bucket: {}", type, key);
                lastAccessTime.remove(key);

                return true;
            }

            return false;
        });
    }

    /**
     * 逐项读取全局桶及三个维度数量，返回独立的可变快照对象。
     * <p>读取可用令牌会触发补充；没有全局桶时当前实现所有字段均返回零，
     * 即使其他集合已有桶。多字段读取不是原子快照。
     *
     * @return 本次采集的统计对象
     */
    public BandwidthLimitStats getStats() {
        if (globalBucket == null)
            return new BandwidthLimitStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        else
            return new BandwidthLimitStats(
                    globalBucket.getCapacity(),
                    globalBucket.getRefillRate(),
                    globalBucket.getAvailableTokens(),
                    globalBucket.getTotalBytesConsumed(),
                    globalBucket.getActualRate(),
                    globalBucket.getTotalWaitTimeNanos(),
                    globalBucket.getUtilization(),
                    apiBuckets.size(),
                    userBuckets.size(),
                    ipBuckets.size()
            );
    }

    /**
     * 存在全局桶时恢复初始容量令牌和初始速率。
     * <p>不清除累计统计，也不影响其他维度。
     */
    public void resetGlobalBucket() {
        if (globalBucket != null) {
            globalBucket.reset();
            log.info("Reset global bandwidth limit bucket");
        }
    }

    /**
     * 清除 API、用户、IP 桶映射及访问时间，不清除全局桶。
     * <p>已有流仍可继续使用之前获得的桶引用。
     */
    public void clearAllBuckets() {
        apiBuckets.clear();
        userBuckets.clear();
        ipBuckets.clear();
        lastAccessTime.clear();
        log.info("Cleared all bandwidth limit buckets");
    }

    /**
     * 关闭定时执行器并等待五秒，超时则尝试中断清理任务。
     * <p>被中断时强制关闭并恢复线程中断标记；不关闭响应流或清空桶。
     */
    public void shutdown() {
        cleanupExecutor.shutdown();

        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS))
                cleanupExecutor.shutdownNow();
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 以 1024 为基数按 B、KB、MB、GB 格式化，非 B 值保留两位小数。
     *
     * @param bytes 待展示的字节量
     * @return 带单位的文本，不自动附加每秒后缀
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        else if (bytes < 1024 * 1024)
            return String.format("%.2f KB", bytes / 1024.0);
        else if (bytes < 1024 * 1024 * 1024)
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        else
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * 请求中保存包装响应的属性键，Helper 与拦截器必须保持一致。
     */
    private static final String WRAPPED_RESPONSE_ATTR = "BandwidthLimitWrappedResponse";

    /**
     * 读取请求中的限速包装响应，未设置时返回调用者传入的默认响应。
     * <p>不创建包装器或验证限速参数，属性类型不符时可能发生类型转换异常。
     *
     * @param request         当前 HTTP 请求及共享的请求属性
     * @param defaultResponse 没有包装响应时返回的响应对象
     * @return 请求中的包装响应或 defaultResponse
     */
    public static HttpServletResponse getLimitedResponse(HttpServletRequest request, HttpServletResponse defaultResponse) {
        BandwidthLimitResponseWrapper wrappedResponse = (BandwidthLimitResponseWrapper) request.getAttribute(WRAPPED_RESPONSE_ATTR);

        if (wrappedResponse != null)
            return wrappedResponse;

        return defaultResponse;
    }

    /**
     * 检查包装响应属性是否非空，不验证正文是否实际通过包装器写入。
     *
     * @param request 当前 HTTP 请求及共享的请求属性
     * @return 存在该请求属性时为 true
     */
    public static boolean isLimited(HttpServletRequest request) {
        return request.getAttribute(WRAPPED_RESPONSE_ATTR) != null;
    }
}
