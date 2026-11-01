package com.ajaxjs.devtools.apidashboard;

import com.ajaxjs.devtools.apidashboard.model.HierarchicalMethodMetrics;
import com.ajaxjs.devtools.apidashboard.model.TimeRangeMetrics;
import com.ajaxjs.util.ObjectHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * API 指标查询与管理入口，路径为 {@code /api/metrics}。
 * <p>支持按方法标识过滤、全量统计、时间桶范围统计、汇总与清理。两个时间参数
 * 必须同时提供才使用范围查询，时间单位为 Unix 纪元毫秒。范围统计按完整时间桶
 * 聚合，不保证精确到请求发生的毫秒；本控制器不参与拦截器自身的请求统计。</p>
 * <p>返回值是接口实际数据，统一响应包装由宿主应用的响应处理机制决定。</p>
 */
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {
    /**
     * 拦截器、指标接口和定时清理共同使用的内存注册表，通过构造器注入。
     */
    private final MetricsRegistry metricsRegistry;

    /**
     * 查询匹配方法的详细指标，支持全量与时间范围两种模式。
     * <p>methodFilter 采用当前默认区域规则转小写后做包含匹配；两个时间参数
     * 只提供一个时仍返回全量数据。没有范围记录的方法也会以零值保留在结果中。</p>
     *
     * @param startTime 查询起点，Unix 纪元毫秒；控制器中可为 null
     * @param endTime 查询终点，Unix 纪元毫秒；控制器中可为 null
     * @param methodFilter 可选的方法标识包含匹配条件，不区分大小写
     * @return 方法标识到指标字段映射；平均耗时和成功率保留两位小数
     */
    @GetMapping
    public Map<String, Object> getMetrics(@RequestParam(required = false) Long startTime, @RequestParam(required = false) Long endTime, @RequestParam(required = false) String methodFilter) {
        Map<String, Object> result = new HashMap<>();
        Map<String, HierarchicalMethodMetrics> snapshot = metricsRegistry.getMetricsSnapshot();

        if (StringUtils.hasText(methodFilter)) // 应用接口名过滤
            snapshot = snapshot.entrySet().stream()
                    .filter(entry -> entry.getKey().toLowerCase().contains(methodFilter.toLowerCase()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (startTime == null || endTime == null) // 如果没有指定时间范围，使用全量数据
            snapshot.forEach((methodName, metrics) -> {
                Map<String, Object> metricData = buildMetricData(metrics);
                result.put(methodName, metricData);
            });
        else
            snapshot.forEach((methodName, metrics) -> { // 使用时间范围查询
                TimeRangeMetrics timeRangeMetrics = metrics.queryTimeRange(startTime, endTime);
                Map<String, Object> metricData = buildTimeRangeMetricData(timeRangeMetrics);
                result.put(methodName, metricData);
            });

        return result;
    }

    /**
     * 将可变累计指标转换为接口展示字段。
     * <p>包含 total、success、fail、avgTime、maxTime、minTime、successRate 和 lastAccess。
     * 各字段分别读取，不提供并发更新下的整体一致性保证。</p>
     *
     * @param metrics 待转换的累计指标对象
     * @return 累计指标的展示映射
     */
    private Map<String, Object> buildMetricData(HierarchicalMethodMetrics metrics) {
        Map<String, Object> metricData = new HashMap<>();
        metricData.put("total", metrics.getTotalCount().get());
        metricData.put("success", metrics.getSuccessCount().get());
        metricData.put("fail", metrics.getFailCount().get());
        metricData.put("avgTime", Math.round(metrics.getAvgTime() * 100.0) / 100.0);
        metricData.put("maxTime", metrics.getMaxTime());
        metricData.put("minTime", metrics.getMinTime());
        metricData.put("successRate", Math.round(metrics.getSuccessRate() * 100.0) / 100.0);
        metricData.put("lastAccess", metrics.getLastAccessTime());

        return metricData;
    }

    /**
     * 将时间桶聚合结果转换为接口字段，附带调用方的起止时间。
     * <p>平均耗时与成功率四舍五入到两位小数，不包含全量模式的 lastAccess 字段。</p>
     *
     * @param timeRangeMetrics 待转换的范围聚合结果
     * @return 时间范围指标的展示映射
     */
    private Map<String, Object> buildTimeRangeMetricData(TimeRangeMetrics timeRangeMetrics) {
        Map<String, Object> metricData = new HashMap<>();
        metricData.put("total", timeRangeMetrics.getTotalCount());
        metricData.put("success", timeRangeMetrics.getSuccessCount());
        metricData.put("fail", timeRangeMetrics.getFailCount());
        metricData.put("avgTime", Math.round(timeRangeMetrics.getAvgTime() * 100.0) / 100.0);
        metricData.put("maxTime", timeRangeMetrics.getMaxTime());
        metricData.put("minTime", timeRangeMetrics.getMinTime());
        metricData.put("successRate", Math.round(timeRangeMetrics.getSuccessRate() * 100.0) / 100.0);
        metricData.put("startTime", timeRangeMetrics.getStartTime());
        metricData.put("endTime", timeRangeMetrics.getEndTime());

        return metricData;
    }

    /**
     * 汇总匹配方法的调用量、失败量、方法数与平均响应时间。
     * <p>平均响应时间为各方法平均耗时的算术平均，不按请求次数加权。
     * 范围查询仅将区间内有调用的方法计入方法数；全量查询使用当前匹配的注册条目数。</p>
     *
     * @param startTime 查询起点，Unix 纪元毫秒；控制器中可为 null
     * @param endTime 查询终点，Unix 纪元毫秒；控制器中可为 null
     * @param methodFilter 可选的方法标识包含匹配条件，不区分大小写
     * @return 包含 totalMethods、totalCalls、totalErrors 和 avgResponseTime 的映射；范围模式额外包含 timeRange
     */
    @GetMapping("/summary")
    public Map<String, Object> getSummary(@RequestParam(required = false) Long startTime, @RequestParam(required = false) Long endTime, @RequestParam(required = false) String methodFilter) {
        Map<String, HierarchicalMethodMetrics> snapshot = metricsRegistry.getMetricsSnapshot();

        // 应用接口名过滤
        if (StringUtils.hasText(methodFilter))
            snapshot = snapshot.entrySet().stream()
                    .filter(entry -> entry.getKey().toLowerCase().contains(methodFilter.toLowerCase()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Map<String, Object> summary = new HashMap<>();

        if (startTime == null || endTime == null) {
            // 全量数据汇总
            summary.put("totalMethods", snapshot.size());
            summary.put("totalCalls", snapshot.values().stream()
                    .mapToLong(m -> m.getTotalCount().get())
                    .sum());
            summary.put("totalErrors", snapshot.values().stream()
                    .mapToLong(m -> m.getFailCount().get())
                    .sum());
            summary.put("avgResponseTime", snapshot.values().stream()
                    .mapToDouble(HierarchicalMethodMetrics::getAvgTime)
                    .average()
                    .orElse(0.0));
        } else {
                    // 时间段数据汇总
            long totalCalls = 0;
            long totalErrors = 0;
            double totalAvgTime = 0;
            int methodCount = 0;

            for (HierarchicalMethodMetrics metrics : snapshot.values()) {
                TimeRangeMetrics timeRange = metrics.queryTimeRange(startTime, endTime);

                if (timeRange.getTotalCount() > 0) {
                    totalCalls += timeRange.getTotalCount();
                    totalErrors += timeRange.getFailCount();
                    totalAvgTime += timeRange.getAvgTime();
                    methodCount++;
                }
            }

            summary.put("totalMethods", methodCount);
            summary.put("totalCalls", totalCalls);
            summary.put("totalErrors", totalErrors);
            summary.put("avgResponseTime", methodCount > 0 ? totalAvgTime / methodCount : 0.0);
            summary.put("timeRange", ObjectHelper.mapOf("startTime", startTime, "endTime", endTime));
        }

        return summary;
    }

    /**
     * 以当前系统时间为终点，查询最近指定分钟数的指标。
     * <p>复用 getMetrics 的时间桶查询语义；当前实现不额外校验 minutes 是否为正数。</p>
     *
     * @param minutes 向前回溯的分钟数
     * @param methodFilter 可选的方法标识包含匹配条件，不区分大小写
     * @return 按方法标识索引的范围指标映射
     */
    @GetMapping("/recent/{minutes}")
    public Map<String, Object> getRecentMetrics(@PathVariable int minutes, @RequestParam(required = false) String methodFilter) {
        long endTime = System.currentTimeMillis();
        long startTime = endTime - (minutes * 60L * 1000L);

        return getMetrics(startTime, endTime, methodFilter);
    }

    /**
     * 清空注册表的全部方法指标及其时间桶。
     * <p>不阻止新请求记录，也不取消正在处理的请求，完成中的请求可能重新创建条目。</p>
     *
     * @return 包含操作完成 message 的映射
     */
    @DeleteMapping
    public Map<String, String> clearMetrics() {
        metricsRegistry.clearMetrics();
        Map<String, String> response = new HashMap<>();
        response.put("message", "All metrics cleared successfully");

        return response;
    }

    /**
     * 按最后记录时间移除闲置超过阈值的方法条目。
     * <p>比较条件为严格大于阈值，边界相等不删除；当前实现不校验阈值正负。
     * 并发记录或淘汰并不形成全局事务。</p>
     *
     * @param maxAgeMs 最大闲置时间，单位为毫秒
     * @return 包含清理完成 message 的映射
     */
    @DeleteMapping("/stale")
    public Map<String, String> removeStaleMetrics(@RequestParam(defaultValue = "3600000") long maxAgeMs) {
        metricsRegistry.removeStaleMetrics(maxAgeMs);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Stale metrics removed successfully");

        return response;
    }
}