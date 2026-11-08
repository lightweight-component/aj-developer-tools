package com.ajaxjs.devtools.netspeed.core;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 全局桶指标和各维度桶数量的可变统计快照。
 * <p>Lombok 生成无参、全参构造器及访问器，不依赖 record 语法。
 * 数据通过管理器逐项读取，不是跨字段原子快照；未创建全局桶时，当前管理器返回全部零值。
 */
@Data
@AllArgsConstructor
public class BandwidthLimitStats {
    /**
     * 采集时全局桶容量，单位字节。
     */
    private long globalCapacity;

    /**
     * 采集时全局桶填充速率，字节/秒。
     */
    private long globalRefillRate;

    /**
     * 采集时全局桶可用令牌，单位字节。
     */
    private long globalAvailableTokens;

    /**
     * 全局累计申请字节数，重置桶不会清零。
     */
    private long globalBytesConsumed;

    /**
     * 全局生命周期平均消耗速率，按整秒计算，字节/秒。
     */
    private double globalActualRate;

    /**
     * 全局累计计算等待量，单位纳秒。
     */
    private long globalWaitTimeNanos;

    /**
     * 全局剩余令牌占容量的比例，并非已用容量比例。
     */
    private double globalUtilization;

    /**
     * 采集时接口桶集合大小。
     */
    private int apiBucketCount;

    /**
     * 采集时用户桶集合大小。
     */
    private int userBucketCount;

    /**
     * 采集时 IP 桶集合大小。
     */
    private int ipBucketCount;
}
