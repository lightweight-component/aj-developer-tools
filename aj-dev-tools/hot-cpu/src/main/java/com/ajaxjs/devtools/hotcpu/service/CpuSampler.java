package com.ajaxjs.devtools.hotcpu.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 采样器
 */
@Component
@Slf4j
public class CpuSampler {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cpu-sampler");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, AtomicInteger> stackCount = new ConcurrentHashMap<>();
    private final AtomicBoolean isEnabled = new AtomicBoolean(false);
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    private static final int SAMPLING_INTERVAL_MS = 10;    // 降低采样间隔到10ms，提高捕获概率
    private static final int MAX_STACK_DEPTH = 100;
    private static final int MAX_STACK_ENTRIES = 10000;

    @PostConstruct
    public void start() {
        log.info("Starting CPU sampler with {}ms interval", SAMPLING_INTERVAL_MS);
        isEnabled.set(true); // 默认启用采样
        executor.scheduleAtFixedRate(this::sample, 0, SAMPLING_INTERVAL_MS, TimeUnit.MILLISECONDS);
        executor.scheduleAtFixedRate(this::cleanupOldData, 5, 5, TimeUnit.MINUTES);// 定期清理数据，避免内存膨胀
    }

    @PreDestroy
    public void stop() {
        log.info("Stopping CPU sampler");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void sample() {
        if (!isEnabled.get())
            return;

        int processedThreads = 0;
        int recordedStacks = 0;
        int testControllerStacks = 0;

        try {
            long[] threadIds = threadMXBean.getAllThreadIds();
            int totalThreads = threadIds.length;

            for (long tid : threadIds) {
                ThreadInfo info = threadMXBean.getThreadInfo(tid, MAX_STACK_DEPTH);
                if (info == null || info.getStackTrace().length == 0)
                    continue;

                processedThreads++;

                if (shouldSkipThread(info.getThreadName()))   // 现在重新启用所有过滤，因为我们要采集所有线程
                    continue;

                StringBuilder sb = new StringBuilder();
                StackTraceElement[] stackTrace = info.getStackTrace();

                // 反转栈帧顺序，让调用链从根到叶子，包含所有栈帧
                for (int i = stackTrace.length - 1; i >= 0; i--) {
                    StackTraceElement frame = stackTrace[i];

                    if (shouldIncludeFrame(frame)) { // 现在重新启用栈帧包含检查，但 shouldIncludeFrame 已经改为返回 true
                        sb.append(frame.getClassName()) // 使用完整的类名.方法名格式
                                .append(".")
                                .append(frame.getMethodName());

                        // 如果有行号信息，也包含进来
                        if (frame.getLineNumber() > 0)
                            sb.append(":").append(frame.getLineNumber());

                        sb.append(";");
                    }
                }

                if (sb.length() > 0) {
                    String stackLine = sb.toString(); // 记录所有调用栈，完全不进行任何过滤
                    stackCount.computeIfAbsent(stackLine, k -> new AtomicInteger(0)).incrementAndGet();
                    recordedStacks++;
                }
            }

            // 每10秒输出一次采样统计
            if (System.currentTimeMillis() % 10000 < 100) { // 大约每10秒
                //log.info("[SAMPLING STATS] Total threads: {}, Processed: {}, Recorded stacks: {}, TestController stacks: {}, Total unique stacks: {}",totalThreads, processedThreads, recordedStacks, testControllerStacks, stackCount.size());
            }
        } catch (Exception e) {
            log.error("Error during sampling: {}", e.getMessage(), e);
        }
    }

    private boolean shouldSkipThread(String threadName) {
        return false;// 完全不过滤线程，返回 false 表示不跳过任何线程
    }

    private boolean shouldIncludeFrame(StackTraceElement frame) {
        return true;  // 包含所有栈帧，不进行任何过滤
    }

    private void cleanupOldData() {
        if (stackCount.size() > MAX_STACK_ENTRIES) {
            log.info("Cleaning up stack data, current size: {}", stackCount.size());
            stackCount.entrySet().removeIf(entry -> entry.getValue().get() < 2); // 保留计数较高的条目
            log.info("After cleanup, stack data size: {}", stackCount.size());
        }
    }

    public Map<String, AtomicInteger> getStackCount() {
        return stackCount;
    }

    public void enableSampling() {
        log.info("CPU sampling enabled");
        isEnabled.set(true);
    }

    public void disableSampling() {
        log.info("CPU sampling disabled");
        isEnabled.set(false);
    }

    public boolean isEnabled() {
        return isEnabled.get();
    }

    public void clearData() {
        log.info("Clearing stack count data");
        stackCount.clear();
    }

    public int getStackCountSize() {
        return stackCount.size();
    }
}