package com.ajaxjs.devtools.apidashboard.model;

import lombok.Data;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 某个固定起点的时间桶，累计落入该桶的请求次数、耗时和成败状态。
 * <p>桶宽由所属映射决定，本类仅保存起点，不自行校验请求时间或负责过期删除。
 * 写入使用实例锁；toSnapshot 未获取该锁，并发记录时读取字段可能来自不同更新阶段。</p>
 */
@Data
public class TimeBucket {
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
     * 桶起始时间，单位为 Unix 纪元毫秒；由外层按粒度对齐后传入。天级按固定 24 小时划分，不按本地日历日期划分。
     */
    private final long bucketStartTime;
    /**
     * 最近一次桶记录更新的系统纪元毫秒；创建时初始化，不决定桶的时间区间。
     */
    private volatile long lastUpdateTime;

    /**
     * 创建指定起点的空时间桶，并以当前时间初始化最后更新时间。
     *
     * @param bucketStartTime 已对齐的桶起始时间，单位为 Unix 纪元毫秒
     */
    public TimeBucket(long bucketStartTime) {
        this.bucketStartTime = bucketStartTime;
        this.lastUpdateTime = System.currentTimeMillis();
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
        lastUpdateTime = System.currentTimeMillis();
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

    /**
     * 根据总请求次数判断桶是否尚无记录。
     *
     * @return 总次数为 0 时返回 true
     */
    public boolean isEmpty() {
        return totalCount.get() == 0;
    }

    /**
     * 复制桶中的数值统计，生成独立的只读结果对象。
     * <p>不复制原子计数器本身，也不获取 record 的实例锁；各字段可能来自不同更新阶段。</p>
     *
     * @return 当前桶的数值快照
     */
    public TimeBucketSnapshot toSnapshot() {
        return new TimeBucketSnapshot(
                bucketStartTime,
                totalCount.get(),
                successCount.get(),
                failCount.get(),
                totalTime.sum(),
                maxTime,
                getMinTime(),
                getAvgTime(),
                getSuccessRate()
        );
    }
}