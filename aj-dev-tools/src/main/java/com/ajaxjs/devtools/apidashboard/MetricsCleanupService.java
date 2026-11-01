package com.ajaxjs.devtools.apidashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 定期移除长时间未完成新请求的方法指标，防止闲置条目持续占用内存。
 * <p>清理依据方法的最后记录时间，删除的是整条指标及其时间桶，不是从累计统计中
 * 逐个扣除历史请求。定时任务捕获异常并记录日志，避免异常传播到调用方。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsCleanupService {
    /**
     * 拦截器、指标接口和定时清理共同使用的内存注册表，通过构造器注入。
     */
    private final MetricsRegistry metricsRegistry;

    /**
     * 方法指标允许的最大闲置时间，单位为毫秒。配置项 devtools.apidashboard.metrics.max-age，默认 3600000（1 小时）。
     */
    @Value("${devtools.apidashboard.metrics.max-age:3600000}")
    private long maxAge;

    /**
     * 按固定频率清理闲置超过 maxAge 的方法指标。
     * <p>周期配置为 devtools.apidashboard.metrics.cleanup-interval，默认 300000 毫秒。
     * 完成后记录剩余方法数，发生异常时仅记录错误日志。</p>
     */
    @Scheduled(fixedRateString = "${devtools.apidashboard.metrics.cleanup-interval:300000}")
    public void cleanupStaleMetrics() {
        try {
            log.debug("Starting metrics cleanup task...");
            metricsRegistry.removeStaleMetrics(maxAge);

            int currentMethodCount = metricsRegistry.getMetricsSnapshot().size();
            log.info("Metrics cleanup completed. Current methods being monitored: {}", currentMethodCount);
        } catch (Exception e) {
            log.error("Error during metrics cleanup", e);
        }
    }

    /**
     * 每天凌晨 2 点清理闲置超过固定 7 天的方法指标。
     * <p>cron 使用调度器默认时区；删除整条指标，不是删除某个日期之前的单次请求。
     * 该任务与周期清理并存，实际保留时间还受到更短的 maxAge 设置影响。</p>
     */
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点清理
    public void dailyCleanup() {
        try {
            log.info("Starting daily metrics cleanup...");

            // 清理7天以上的数据
            long sevenDaysAgo = 7 * 24 * 60 * 60 * 1000L;
            metricsRegistry.removeStaleMetrics(sevenDaysAgo);
            log.info("Daily metrics cleanup completed");
        } catch (Exception e) {
            log.error("Error during daily metrics cleanup", e);
        }
    }
}