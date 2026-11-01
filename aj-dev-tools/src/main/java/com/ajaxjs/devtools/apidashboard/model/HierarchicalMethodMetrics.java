package com.ajaxjs.devtools.apidashboard.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 单个控制器方法的累计指标与秒、分、时、天四级时间桶。
 * <p>每条记录同时写入四种粒度，查询只选择其中一种，避免跨级重复计数。
 * 记录操作同步执行，但读取和快照未获取同一锁，并发查询不保证所有字段来自同一时刻。</p>
 * <p>范围粒度仅依据查询跨度选择；查询很久以前的短区间可能命中已淘汰的秒桶，
 * 不会自动回退到粗粒度桶。桶清理由新记录触发，方法长期闲置时依赖注册表清理整条指标。</p>
 */
@Slf4j
@Data
public class HierarchicalMethodMetrics {
    // 基础统计信息（兼容旧版本）
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

    // 分级时间桶
    /**
     * 秒级桶映射，键为纪元秒，写入时按约 5 分钟保留窗口淘汰旧桶。
     */
    private final ConcurrentHashMap<Long, TimeBucket> secondBuckets = new ConcurrentHashMap<>();  // 最近5分钟，秒级
    /**
     * 分钟级桶映射，键为纪元分钟，写入时按约 1 小时保留窗口淘汰旧桶。
     */
    private final ConcurrentHashMap<Long, TimeBucket> minuteBuckets = new ConcurrentHashMap<>();  // 最近1小时，分钟级
    /**
     * 小时级桶映射，键为纪元小时，写入时按约 24 小时保留窗口淘汰旧桶。
     */
    private final ConcurrentHashMap<Long, TimeBucket> hourBuckets = new ConcurrentHashMap<>();    // 最近24小时，小时级
    /**
     * 天级桶映射，键为纪元毫秒除以固定 24 小时，写入时按约 7 天窗口淘汰旧桶。
     */
    private final ConcurrentHashMap<Long, TimeBucket> dayBuckets = new ConcurrentHashMap<>();     // 最近7天，天级

    // 时间常量
    /**
     * 一秒对应的毫秒数，用于秒桶键及起点换算。
     */
    private static final long SECOND_MILLIS = 1000;
    /**
     * 一分钟对应的毫秒数，用于分钟桶键及查询跨度判断。
     */
    private static final long MINUTE_MILLIS = 60 * SECOND_MILLIS;
    /**
     * 一小时对应的毫秒数，用于小时桶键及查询跨度判断。
     */
    private static final long HOUR_MILLIS = 60 * MINUTE_MILLIS;
    /**
     * 固定 24 小时对应的毫秒数，不处理时区或夏令时变化。
     */
    private static final long DAY_MILLIS = 24 * HOUR_MILLIS;

    // 保留时长
    /**
     * 秒桶保留窗口的秒数，为 300；边界桶仍保留，实际覆盖可能略长。
     */
    private static final long KEEP_SECONDS = 5 * 60;      // 5分钟
    /**
     * 分钟桶保留窗口的分钟数，为 60；严格早于截止键的桶才删除。
     */
    private static final long KEEP_MINUTES = 60;          // 1小时
    /**
     * 小时桶保留窗口的小时数，为 24；清理仅在记录新请求时触发。
     */
    private static final long KEEP_HOURS = 24;            // 24小时
    /**
     * 天桶保留窗口的固定天数，为 7；不影响方法全量累计计数。
     */
    private static final long KEEP_DAYS = 7;              // 7天

    /**
     * 创建带分级时间桶的方法指标，初始计数均为零。
     *
     * @param methodName 控制器方法标识
     */
    public HierarchicalMethodMetrics(String methodName) {
        this.methodName = methodName;
    }

    /**
     * 追加一次请求记录，更新总次数、成败次数、累计耗时和极值。
     * <p>由调用者传入成功标志和毫秒耗时，不自行解析响应或校验负数耗时。</p>
     * <p>方法级同步保证记录串行写入，最后访问时间取本次记录时刻；
     * 更新累计值后同步写入四级时间桶并淘汰旧桶，按请求完成时刻归桶。</p>
     *
     * @param duration 本次请求耗时，单位为毫秒
     * @param success 本次请求是否成功
     */
    public synchronized void record(long duration, boolean success) {
        long currentTime = System.currentTimeMillis();
        totalCount.incrementAndGet(); // 更新基础统计

        if (success)
            successCount.incrementAndGet();
        else
            failCount.incrementAndGet();

        totalTime.add(duration);
        maxTime = Math.max(maxTime, duration);
        minTime = Math.min(minTime, duration);
        lastAccessTime = currentTime;
        recordToTimeBuckets(currentTime, duration, success); // 分级记录到不同时间桶
        cleanupExpiredBuckets(currentTime); // 清理过期桶
    }

    /**
     * 将同一次请求分别写入秒、分、时、天四级桶。
     * <p>各级键使用纪元毫秒整除粒度生成，通过 computeIfAbsent 创建桶；
     * 四级数据是同一批请求的不同粒度表示，不能跨级直接相加。</p>
     *
     * @param currentTime 用于桶定位和淘汰判断的当前纪元毫秒
     * @param duration 本次请求耗时，单位为毫秒
     * @param success 本次请求是否成功
     */
    private void recordToTimeBuckets(long currentTime, long duration, boolean success) {
        long secondKey = currentTime / SECOND_MILLIS; // 秒级桶
        secondBuckets.computeIfAbsent(secondKey, k -> new TimeBucket(k * SECOND_MILLIS)).record(duration, success);

        long minuteKey = currentTime / MINUTE_MILLIS; // 分钟级桶
        minuteBuckets.computeIfAbsent(minuteKey, k -> new TimeBucket(k * MINUTE_MILLIS)).record(duration, success);


        long hourKey = currentTime / HOUR_MILLIS; // 小时级桶
        hourBuckets.computeIfAbsent(hourKey, k -> new TimeBucket(k * HOUR_MILLIS)).record(duration, success);

        long dayKey = currentTime / DAY_MILLIS;// 天级桶
        dayBuckets.computeIfAbsent(dayKey, k -> new TimeBucket(k * DAY_MILLIS)).record(duration, success);
    }

    /**
     * 按当前时间分别淘汰各级窗口之外的桶。
     * <p>仅删除键严格小于截止键的桶，保留边界桶；不修改全量累计指标。
     * 此方法由 record 调用，空闲期间不会独立运行。</p>
     *
     * @param currentTime 用于桶定位和淘汰判断的当前纪元毫秒
     */
    private void cleanupExpiredBuckets(long currentTime) {
        // 清理过期的秒级桶（保留5分钟）
        long expiredSecond = (currentTime / SECOND_MILLIS) - KEEP_SECONDS;
        secondBuckets.entrySet().removeIf(entry -> entry.getKey() < expiredSecond);

        // 清理过期的分钟级桶（保留1小时）
        long expiredMinute = (currentTime / MINUTE_MILLIS) - KEEP_MINUTES;
        minuteBuckets.entrySet().removeIf(entry -> entry.getKey() < expiredMinute);

        // 清理过期的小时级桶（保留24小时）
        long expiredHour = (currentTime / HOUR_MILLIS) - KEEP_HOURS;
        hourBuckets.entrySet().removeIf(entry -> entry.getKey() < expiredHour);

        // 清理过期的天级桶（保留7天）
        long expiredDay = (currentTime / DAY_MILLIS) - KEEP_DAYS;
        dayBuckets.entrySet().removeIf(entry -> entry.getKey() < expiredDay);
    }

    /**
     * 选择一种时间粒度的桶并聚合查询结果。
     * <p>选择取决于区间跨度，起止桶均包含，不对边界桶按毫秒裁切，
     * 也不校验时间顺序；没有命中桶时返回零值结果。</p>
     *
     * @param startTime 查询起点，Unix 纪元毫秒；控制器中可为 null
     * @param endTime 查询终点，Unix 纪元毫秒；控制器中可为 null
     * @return 保留原始起止参数的时间范围聚合结果
     */
    public TimeRangeMetrics queryTimeRange(long startTime, long endTime) {
        List<TimeBucketSnapshot> buckets = selectBucketsForTimeRange(startTime, endTime);

        return aggregateSnapshots(buckets, startTime, endTime);
    }

    /**
     * 按跨度选择秒桶（不超过 5 分钟）、分钟桶（不超过 1 小时）、
     * 小时桶（不超过 24 小时）或天桶，并逐键收集非空桶快照。
     * <p>只使用选定粒度，不因桶过期回退；查询跨度较大时逐键遍历可能耗时较长。</p>
     *
     * @param startTime 查询起点，Unix 纪元毫秒；控制器中可为 null
     * @param endTime 查询终点，Unix 纪元毫秒；控制器中可为 null
     * @return 命中范围内非空桶的数值快照列表
     */
    private List<TimeBucketSnapshot> selectBucketsForTimeRange(long startTime, long endTime) {
        long duration = endTime - startTime;
        List<TimeBucketSnapshot> result = new ArrayList<>();

        if (duration <= 5 * MINUTE_MILLIS) {
            // 5分钟内，使用秒级桶
            long startSecond = startTime / SECOND_MILLIS;
            long endSecond = endTime / SECOND_MILLIS;

            for (long sec = startSecond; sec <= endSecond; sec++) {
                TimeBucket bucket = secondBuckets.get(sec);

                if (bucket != null && !bucket.isEmpty())
                    result.add(bucket.toSnapshot());
            }
        } else if (duration <= HOUR_MILLIS) {
            // 1小时内，使用分钟级桶
            long startMinute = startTime / MINUTE_MILLIS;
            long endMinute = endTime / MINUTE_MILLIS;

            for (long min = startMinute; min <= endMinute; min++) {
                TimeBucket bucket = minuteBuckets.get(min);

                if (bucket != null && !bucket.isEmpty())
                    result.add(bucket.toSnapshot());
            }
        } else if (duration <= DAY_MILLIS) {
            // 1天内，使用小时级桶
            long startHour = startTime / HOUR_MILLIS;
            long endHour = endTime / HOUR_MILLIS;

            for (long hour = startHour; hour <= endHour; hour++) {
                TimeBucket bucket = hourBuckets.get(hour);

                if (bucket != null && !bucket.isEmpty())
                    result.add(bucket.toSnapshot());
            }
        } else {
            // 超过1天，使用天级桶
            long startDay = startTime / DAY_MILLIS;
            long endDay = endTime / DAY_MILLIS;

            for (long day = startDay; day <= endDay; day++) {
                TimeBucket bucket = dayBuckets.get(day);

                if (bucket != null && !bucket.isEmpty())
                    result.add(bucket.toSnapshot());
            }
        }

        return result;
    }

    /**
     * 合并同粒度桶的计数、总耗时和极值，再计算平均耗时与成功率。
     * <p>平均耗时按总耗时除以总次数计算，桶之间按调用次数自然加权；
     * 这与控制器对不同方法平均值求算术平均的汇总口径不同。</p>
     *
     * @param snapshots 同一时间粒度的桶快照列表
     * @param startTime 查询起点，Unix 纪元毫秒；控制器中可为 null
     * @param endTime 查询终点，Unix 纪元毫秒；控制器中可为 null
     * @return 范围统计；空快照列表返回全部统计值为零的结果
     */
    private TimeRangeMetrics aggregateSnapshots(List<TimeBucketSnapshot> snapshots, long startTime, long endTime) {
        if (snapshots.isEmpty())
            return new TimeRangeMetrics(methodName, startTime, endTime, 0, 0, 0, 0, 0, 0, 0, 0);

        long totalCount = 0;
        long successCount = 0;
        long failCount = 0;
        long totalTime = 0;
        long maxTime = 0;
        long minTime = Long.MAX_VALUE;

        for (TimeBucketSnapshot snapshot : snapshots) {
            totalCount += snapshot.getTotalCount();
            successCount += snapshot.getSuccessCount();
            failCount += snapshot.getFailCount();
            totalTime += snapshot.getTotalTime();
            maxTime = Math.max(maxTime, snapshot.getMaxTime());
            minTime = Math.min(minTime, snapshot.getMinTime());
        }

        double avgTime = totalCount > 0 ? (double) totalTime / totalCount : 0;
        double successRate = totalCount > 0 ? (double) successCount / totalCount * 100 : 0;

        return new TimeRangeMetrics(
                methodName, startTime, endTime,
                totalCount, successCount, failCount,
                totalTime, maxTime, minTime == Long.MAX_VALUE ? 0 : minTime,
                avgTime, successRate
        );
    }

    // 兼容旧版本的方法
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