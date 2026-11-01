package com.ajaxjs.devtools.apidashboard.model;

import lombok.Data;

/**
 * 时间桶的只读数值副本，用于范围聚合，字段不再引用原桶的原子计数器。
 * <p>Lombok 为 final 字段生成全参数构造器和访问器，不生成 setter。
 * 原桶采集快照时未整体加锁，因此字段值不保证是某个瞬间的严格一致快照。</p>
 */
@Data
public class TimeBucketSnapshot {
    /**
     * 桶起始时间，单位为 Unix 纪元毫秒；由外层按粒度对齐后传入。天级按固定 24 小时划分，不按本地日历日期划分。
     */
    private final long bucketStartTime;
    /**
     * 累计请求次数，成功与失败请求均计入。可变模型使用 AtomicLong 更新，结果模型保存数值副本。
     */
    private final long totalCount;
    /**
     * 累计成功请求次数，由采集方传入的 success 标志决定。
     */
    private final long successCount;
    /**
     * 累计失败请求次数，与成功次数一起构成总请求次数。
     */
    private final long failCount;
    /**
     * 所有已记录请求的累计耗时，单位为毫秒，包含成功和失败请求。
     */
    private final long totalTime;
    /**
     * 最大单次请求耗时，单位为毫秒；初始或无记录时为 0。
     */
    private final long maxTime;
    /**
     * 最小单次请求耗时，单位为毫秒。可变模型用 Long.MAX_VALUE 表示尚无记录，访问器及快照转换为 0。
     */
    private final long minTime;
    /**
     * 请求平均耗时，单位为毫秒，由累计耗时除以累计请求数；没有请求时为 0。
     */
    private final double avgTime;
    /**
     * 成功率，采用 0 至 100 的百分数尺度；没有请求时为 0。
     */
    private final double successRate;
}