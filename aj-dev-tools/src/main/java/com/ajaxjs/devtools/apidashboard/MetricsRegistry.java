package com.ajaxjs.devtools.apidashboard;

import com.ajaxjs.devtools.apidashboard.model.HierarchicalMethodMetrics;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 当前 JVM 内的请求指标注册表，供拦截器、查询接口和清理任务共享。
 * <p>并发映射负责按方法标识定位指标，单个指标通过自身的同步记录方法更新。
 * 没有持久化或跨实例聚合，重启后数据丢失；全量统计持续累积，直到清空或整条过期删除。</p>
 */
@Component
public class MetricsRegistry {
    /**
     * 按方法标识索引的并发指标表。映射快照只复制表结构，内部指标对象仍共享。
     */
    private final ConcurrentHashMap<String, HierarchicalMethodMetrics> metricsMap = new ConcurrentHashMap<>();

    /**
     * 追加一次请求记录，更新总次数、成败次数、累计耗时和极值。
     * <p>由调用者传入成功标志和毫秒耗时，不自行解析响应或校验负数耗时。</p>
     *
     * @param methodName 控制器方法标识
     * @param duration 本次请求耗时，单位为毫秒
     * @param success 本次请求是否成功
     */
    public void record(String methodName, long duration, boolean success) {
        metricsMap.computeIfAbsent(methodName, HierarchicalMethodMetrics::new).record(duration, success);
    }

    /**
     * 复制注册表结构以便查询和筛选，不复制内部指标对象。
     * <p>修改返回映射不影响原映射结构，但指标值仍可能被正在执行的请求更新。</p>
     *
     * @return 包含共享指标对象的独立并发映射
     */
    public Map<String, HierarchicalMethodMetrics> getMetricsSnapshot() {
        return new ConcurrentHashMap<>(metricsMap);
    }

    /**
     * 清空注册表的全部方法指标及其时间桶。
     * <p>不阻止新请求记录，也不取消正在处理的请求，完成中的请求可能重新创建条目。</p>
     */
    public void clearMetrics() {
        metricsMap.clear();
    }

    /**
     * 按最后记录时间移除闲置超过阈值的方法条目。
     * <p>比较条件为严格大于阈值，边界相等不删除；当前实现不校验阈值正负。
     * 并发记录或淘汰并不形成全局事务。</p>
     *
     * @param maxAgeMs 最大闲置时间，单位为毫秒
     */
    public void removeStaleMetrics(long maxAgeMs) {
        long now = System.currentTimeMillis();
        metricsMap.entrySet().removeIf(entry -> now - entry.getValue().getLastAccessTime() > maxAgeMs);
    }
}
