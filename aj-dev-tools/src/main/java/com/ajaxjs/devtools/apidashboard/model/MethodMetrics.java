package com.ajaxjs.devtools.apidashboard.model;

import lombok.Data;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 不含时间桶的基础累计指标模型，保留简单计数、耗时和最后访问时间。
 * <p>当前请求采集使用 HierarchicalMethodMetrics；此类保留为基础兼容模型。
 * record 使用实例锁串行更新，独立读取多个字段不构成一致性快照。</p>
 */
@Data
public class MethodMetrics {
    /**
     * 累计请求次数，成功与失败请求均计入。可变模型使用 AtomicLong 更新，结果模型保存数值副本。
     */
    private final AtomicLong totalCount = new AtomicLong(0);
    /**
     * 累计成功请求次数，由采集方传入的 success 标志决定。
     */
    private final AtomicLong successCount = new AtomicLong(0);
    /**
     * 累计失败请求次数，与成功次数一起构成总请求次数。
     */
    private final AtomicLong failCount = new AtomicLong(0);
    /**
     * 所有已记录请求的累计耗时，单位为毫秒，包含成功和失败请求。
     */
    private final LongAdder totalTime = new LongAdder();
    /**
     * 最大单次请求耗时，单位为毫秒；初始或无记录时为 0。
     */
    private volatile long maxTime = 0;
    /**
     * 最小单次请求耗时，单位为毫秒。可变模型用 Long.MAX_VALUE 表示尚无记录，访问器及快照转换为 0。
     */
    private volatile long minTime = Long.MAX_VALUE;
    /**
     * 控制器方法标识，当前格式为简单类名.方法名()，不包含参数签名。
     */
    private final String methodName;
    /**
     * 最近一次记录请求时的系统纪元毫秒，用于方法级闲置淘汰；创建时初始化为当前时间。
     */
    private volatile long lastAccessTime = System.currentTimeMillis();

    /**
     * 创建基础方法指标，最小耗时保持尚无记录的哨兵值。
     *
     * @param methodName 控制器方法标识
     */
    public MethodMetrics(String methodName) {
        this.methodName = methodName;
    }

    /**
     * 追加一次请求记录，更新总次数、成败次数、累计耗时和极值。
     * <p>由调用者传入成功标志和毫秒耗时，不自行解析响应或校验负数耗时。</p>
     * <p>通过实例锁串行写入，并以当前系统时间刷新最近更新时间；读取方未获取此锁。</p>
     *
     * @param duration 本次请求耗时，单位为毫秒
     * @param success 本次请求是否成功
     */
    public synchronized void record(long duration, boolean success) {
        totalCount.incrementAndGet();

        if (success)
            successCount.incrementAndGet();
        else
            failCount.incrementAndGet();

        totalTime.add(duration);
        maxTime = Math.max(maxTime, duration);
        minTime = Math.min(minTime, duration);
        lastAccessTime = System.currentTimeMillis();
    }

    /**
     * 计算当前累计请求的平均耗时。
     * <p>计数和总耗时分别读取，并发写入时可能出现短暂的统计差异。</p>
     *
     * @return 平均耗时，单位为毫秒；无请求时为 0
     */
    public double getAvgTime() {
        long total = totalCount.get();

        return total == 0 ? 0.0 : (double) totalTime.sum() / total;
    }

    /**
     * 按成功次数除以总请求次数计算百分比。
     *
     * @return 成功率，使用 0 至 100 的展示尺度；无请求时为 0
     */
    public double getSuccessRate() {
        long total = totalCount.get();

        return total == 0 ? 0.0 : (double) successCount.get() / total * 100;
    }

    /**
     * 读取最小耗时，将尚无记录的 Long.MAX_VALUE 哨兵转换为 0。
     *
     * @return 最小请求耗时，单位为毫秒；无请求时为 0
     */
    public long getMinTime() {
        return minTime == Long.MAX_VALUE ? 0 : minTime;
    }
}