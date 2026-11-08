package com.ajaxjs.devtools.netspeed.core;

import lombok.Getter;

import java.util.concurrent.locks.LockSupport;

/**
 * 以字节作为令牌单位的时间补充桶。
 * <p>初始令牌数等于容量，允许短时突发；补充根据 nanoTime 时间差按整数运算计算，
 * 令牌上限为固定容量。acquire 和 tryAcquire 同步执行，阻塞等待期间仍持有实例锁。
 * <p>本类不校验容量、速率及算术溢出。部分读取方法会修改补充状态且未同步，
 * volatile 字段并不保证多个字段组合读取为一致快照。
 */
public class TokenBucket {
    /**
     * 固定桶容量，单位字节，创建后不随速率调整改变。
     */
    @Getter
    private final long capacity;
    /**
     * 构造时速率，reset 时恢复，单位字节/秒。
     */
    private final long initialRefillRate;

    /**
     * 当前填充速率，单位字节/秒。
     */
    @Getter
    private volatile long refillRate;

    /**
     * 当前剩余字节令牌，补充最多至 capacity。
     */
    private volatile long tokens;

    /**
     * 上次有效补充的 nanoTime 时间点。
     */
    private volatile long lastRefillTime;

    /**
     * 累计成功申请的字节数，reset 不清零，不等同于网络实际写入量。
     */
    @Getter
    private volatile long totalBytesConsumed;

    /**
     * 累计计算等待量，单位纳秒，不是实测线程阻塞时间，reset 不清零。
     */
    @Getter
    private volatile long totalWaitTimeNanos;
    /**
     * 创建桶的单调时钟时间，用于生命周期平均速率。
     */
    private final long creationTime;

    /**
     * 创建固定容量的桶，初始填满令牌并记录当前单调时钟。
     * <p>保存初始速率供 reset 使用，输入应为正数，当前构造不执行校验。
     *
     * @param capacity   桶容量，单位为字节，应为正数
     * @param refillRate 填充速率，单位为字节/秒，应为正数
     */
    public TokenBucket(long capacity, long refillRate) {
        this.capacity = capacity;
        this.initialRefillRate = refillRate;
        this.refillRate = refillRate;
        this.tokens = capacity;
        this.lastRefillTime = System.nanoTime();
        this.totalBytesConsumed = 0;
        this.totalWaitTimeNanos = 0;
        this.creationTime = System.nanoTime();
    }

    /**
     * 阻塞式申请指定字节数的令牌，非正数直接返回。
     * <p>先补充令牌，计算不足部分的等待时间，等待后再次补充并扣减，统计请求字节与计算等待量。
     * 等待期间持有对象锁；请求超过容量时等待后将令牌扣至零，不进行多轮重新校验。
     *
     * @param permits 请求令牌数，单位为字节，非正数不消耗令牌
     */
    public synchronized void acquire(long permits) {
        if (permits <= 0)
            return;

        long waitTime = refillAndCalculateWait(permits);

        if (waitTime > 0) {
            sleepNanos(waitTime);
            totalWaitTimeNanos += waitTime;
            refill();// 等待后再次填充并消费
            tokens = Math.max(0, tokens - permits);
        } else
            tokens -= permits;

        totalBytesConsumed += permits;
    }

    /**
     * 立即尝试补充并扣减令牌，不进行等待。
     * <p>非正数视为成功；足够时同时累计消耗量，不足时返回 false 且不消费。
     *
     * @param permits 请求令牌数，单位为字节，非正数不消耗令牌
     * @return 是否成功获取令牌
     */
    public synchronized boolean tryAcquire(long permits) {
        if (permits <= 0)
            return true;

        refill();

        if (tokens >= permits) {
            tokens -= permits;
            totalBytesConsumed += permits;
            return true;
        }

        return false;
    }

    /**
     * 补充令牌后，按不足量乘十亿再除当前速率计算等待纳秒。
     * <p>整数除法截断；速率为零时可能除零，乘法可能溢出。
     *
     * @param permits 请求令牌数，单位为字节，非正数不消耗令牌
     * @return 令牌足够时为零，否则为计算的等待纳秒数
     */
    private long refillAndCalculateWait(long permits) {
        refill();

        if (tokens >= permits)
            return 0;

        long deficit = permits - tokens; // 令牌不足，计算需要等待的时间

        return (deficit * 1_000_000_000L) / refillRate;
    }

    /**
     * 按距上次补充的纳秒数计算新增令牌，限制总量不超过容量。
     * <p>时间差非正或新增量不足一字节时不更新；只有新增量为正才推进最后补充时间。
     * 方法本身不加锁，由部分同步方法调用，也可能由未同步读取方法调用。
     */
    private void refill() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillTime;

        if (elapsedNanos <= 0)
            return;

        long newTokens = (elapsedNanos * refillRate) / 1_000_000_000L; // 根据时间差计算补充的令牌数

        if (newTokens > 0) {
            tokens = Math.min(capacity, tokens + newTokens);
            lastRefillTime = now;
        }
    }

    /**
     * 循环 parkNanos，直到单调时钟达到目标时间，非正等待直接返回。
     * <p>每次至少请求一千纳秒，实际精度取决于调度器；不检查中断标记，不能作为可中断等待。
     *
     * @param nanos 请求等待的纳秒数，非正数直接返回
     */
    private void sleepNanos(long nanos) {
        if (nanos <= 0)
            return;

        long end = System.nanoTime() + nanos;

        while (System.nanoTime() < end)
            LockSupport.parkNanos(Math.max(1000, end - System.nanoTime()));
    }

    /**
     * 先补充令牌再返回当前数量。
     * <p>此读取包含状态修改，方法未同步，不是与消费操作原子一致的读取。
     *
     * @return 当前可用字节令牌数
     */
    public long getAvailableTokens() {
        refill();

        return tokens;
    }

    /**
     * 先替换填充速率，再按新速率补充之前经过的时间。
     * <p>不调整容量，也不改变用于 reset 的初始速率，未校验正数。
     *
     * @param newRate 新的正数填充速率，字节/秒
     */
    public synchronized void setRefillRate(long newRate) {
        refillRate = newRate;
        refill();
    }

    /**
     * 将可用令牌恢复到容量，将速率恢复为构造时值，并重置补充时间。
     * <p>不清零累计消耗、累计等待或创建时间，因此平均速率统计不会重新开始。
     */
    public synchronized void reset() {
        tokens = capacity;
        refillRate = initialRefillRate;
        lastRefillTime = System.nanoTime();
    }

    /**
     * 按累计申请成功的字节数除以自创建以来的整秒数计算平均速率。
     * <p>不足一秒或时间差非正时返回零；使用整秒截断，不是瞬时速率。
     *
     * @return 累计平均消耗速率，字节/秒
     */
    public double getActualRate() {
        long elapsedNanos = System.nanoTime() - creationTime;

        if (elapsedNanos <= 0)
            return 0;

        long elapsedSeconds = elapsedNanos / 1_000_000_000L;

        return elapsedSeconds > 0 ? (double) totalBytesConsumed / elapsedSeconds : 0;
    }

    /**
     * 返回当前令牌数与容量之比，不主动补充令牌。
     * <p>名称为利用率，实际表示剩余令牌比例；容量非正时返回零。
     *
     * @return 当前剩余令牌比例
     */
    public double getUtilization() {
        return capacity > 0 ? (double) tokens / capacity : 0;
    }
}
