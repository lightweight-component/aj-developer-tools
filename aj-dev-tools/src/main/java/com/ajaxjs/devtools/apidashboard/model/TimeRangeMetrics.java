package com.ajaxjs.devtools.apidashboard.model;

import lombok.Data;

/**
 * 某方法在指定查询区间内的时间桶聚合结果。
 * <p>起止时间保留调用方参数，计数与耗时来自命中桶的完整数据，边界桶可能包含
 * 区间之外的记录。Lombok 为全部 final 字段生成构造器与访问器，结果本身不再随桶更新。</p>
 */
@Data
public class TimeRangeMetrics {
    /**
     * 控制器方法标识，当前格式为简单类名.方法名()，不包含参数签名。
     */
    private final String methodName;
    /**
     * 调用方指定的查询起点，单位为 Unix 纪元毫秒；命中桶按整桶计入。
     */
    private final long startTime;
    /**
     * 调用方指定的查询终点，单位为 Unix 纪元毫秒；终点所在桶也参与聚合。
     */
    private final long endTime;
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