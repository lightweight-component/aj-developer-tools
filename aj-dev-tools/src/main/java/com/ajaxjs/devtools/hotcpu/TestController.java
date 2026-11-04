package com.ajaxjs.devtools.hotcpu;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/hotcpu/test")
public class TestController {
    @GetMapping("/cpu-intensive")
    public Map<String, Object> cpuIntensiveTask(@RequestParam(name = "iterations", defaultValue = "1000") int iterations) {
        log.info("开始执行CPU密集型任务，迭代次数: {}", iterations);
        long startTime = System.currentTimeMillis();
        double result = performComplexCalculation(iterations); // 模拟CPU密集型任务
        long endTime = System.currentTimeMillis();

        Map<String, Object> response = new HashMap<>();
        response.put("result", result);
        response.put("iterations", iterations);
        response.put("executionTimeMs", endTime - startTime);
        response.put("message", "CPU intensive task completed");

        log.info("CPU intensive task completed in {}ms", endTime - startTime);

        return response;
    }

    @GetMapping("/nested-calls")
    public Map<String, Object> nestedCallsTest(@RequestParam(name = "depth", defaultValue = "10") int depth) {
        long startTime = System.currentTimeMillis();
        double result = recursiveMethod(depth, 1000);
        long endTime = System.currentTimeMillis();

        Map<String, Object> response = new HashMap<>();
        response.put("result", result);
        response.put("depth", depth);
        response.put("executionTimeMs", endTime - startTime);
        response.put("message", "Nested calls test completed");

        return response;
    }

    @GetMapping("/mixed-workload")
    public Map<String, Object> mixedWorkload() throws InterruptedException {
        long startTime = System.currentTimeMillis();

        // 暴力 BigInteger 大数阶乘, 模拟耗时操作
        log.info("开始执行大数阶乘");
        BigInteger.valueOf(1).multiply(java.util.stream.LongStream.rangeClosed(2, 500_000)
                .mapToObj(BigInteger::valueOf)
                .reduce(BigInteger.ONE, BigInteger::multiply));
        log.info("结束执行大数阶乘");

        double cpuResult = performComplexCalculation(10000000); // 混合工作负载：CPU + I/O
        Thread.sleep(100); // 模拟I/O等待
        double ioResult = performDatabaseLikeOperation();
        Thread.sleep(50);
        double finalResult = performComplexCalculation(10000000);
        long endTime = System.currentTimeMillis();

        Map<String, Object> response = new HashMap<>();
        response.put("cpuResult", cpuResult);
        response.put("ioResult", ioResult);
        response.put("finalResult", finalResult);
        response.put("executionTimeMs", endTime - startTime);
        response.put("message", "Mixed workload completed");

        return response;
    }

    private double performComplexCalculation(int iterations) {
        double result = 0.0;

        for (int i = 0; i < iterations; i++) {
            String id = UUID.randomUUID().toString();
            result += Math.sin(i) * Math.cos(i) * Math.sqrt(i + 1);
            result += fibonacci(i % 20); // 递归调用

            String temp = "test" + i;// 模拟一些字符串操作
            temp = temp.toUpperCase().toLowerCase();
            result += temp.hashCode() % 100;
        }

        return result;
    }

    private double recursiveMethod(int depth, int workSize) {
        if (depth <= 0)
            return performBasicCalculation(workSize);

        double result1 = performBasicCalculation(workSize / 2);
        double result2 = recursiveMethod(depth - 1, workSize / 2);

        return result1 + result2;
    }

    private double performBasicCalculation(int size) {
        double result = 0.0;
        for (int i = 0; i < size; i++)
            result += Math.log(i + 1) * Math.exp(i * 0.001);

        return result;
    }

    private double performDatabaseLikeOperation() {
        double result = 0.0;  // 模拟数据库查询类的操作

        for (int i = 0; i < 100; i++)
            result += queryLikeOperation(i);

        return result;
    }

    private double queryLikeOperation(int id) {
        return Math.random() * id + processRecord(id);  // 模拟查询操作
    }

    private double processRecord(int id) {
        return Math.pow(id, 2) + Math.sqrt(id);
    }

    private long fibonacci(int n) {
        if (n <= 1)
            return n;

        return fibonacci(n - 1) + fibonacci(n - 2);
    }

    @GetMapping("/long-running")
    public Map<String, Object> longRunningTask(@RequestParam(name = "duration", defaultValue = "5000") int durationMs) {
        log.info("开始执行长时间运行任务，持续时间: {}ms", durationMs);
        long startTime = System.currentTimeMillis();
        long endTime = startTime + durationMs;
        double result = 0.0;
        int iterations = 0;

        while (System.currentTimeMillis() < endTime) { // 持续运行指定时间，确保采样器能够捕获到
            result += performLongCalculation(100);
            iterations++;

            if (iterations % 100 == 0) // 每100次迭代检查一次时间，避免过于频繁的时间检查
                log.debug("Long running task - iterations: {}, current result: {}", iterations, result);
        }

        long actualDuration = System.currentTimeMillis() - startTime;

        Map<String, Object> response = new HashMap<>();
        response.put("result", result);
        response.put("iterations", iterations);
        response.put("requestedDurationMs", durationMs);
        response.put("actualDurationMs", actualDuration);
        response.put("message", "Long running task completed");

        log.info("Long running task completed - iterations: {}, duration: {}ms", iterations, actualDuration);
        return response;
    }

    private double performLongCalculation(int size) {
        double result = 0.0;
        for (int i = 0; i < size; i++)
            result += deepMethodCall1(i);

        return result;
    }

    private double deepMethodCall1(int value) {
        return deepMethodCall2(value * 2) + Math.sin(value);
    }

    private double deepMethodCall2(int value) {
        return deepMethodCall3(value + 1) + Math.cos(value);
    }

    private double deepMethodCall3(int value) {
        return deepMethodCall4(value % 100) + Math.sqrt(value);
    }

    private double deepMethodCall4(int value) {
        double result = Math.log(value + 1); // 最深层的方法调用，包含一些计算

        if (value % 10 == 0)
            result += fibonacci(value % 15); // 递归调用

        return result;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "test-controller");
        response.put("availableEndpoints", new String[]{
                "/test/cpu-intensive?iterations=1000",
                "/test/nested-calls?depth=10",
                "/test/mixed-workload",
                "/test/long-running?duration=5000",
                "/test/status"
        });

        return response;
    }
}