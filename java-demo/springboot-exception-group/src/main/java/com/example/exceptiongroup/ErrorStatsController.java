package com.example.exceptiongroup.controller;

import com.example.exceptiongroup.fingerprint.ErrorFingerprintCache;
import com.example.exceptiongroup.fingerprint.ErrorFingerprintGenerator;
import com.example.exceptiongroup.fingerprint.TraceIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 错误统计REST API控制器
 */
@RestController
@RequestMapping("/api/error-stats")
@Slf4j
public class ErrorStatsController {
    @Autowired
    private ErrorFingerprintCache fingerprintCache;

    @Autowired
    private TraceIdGenerator traceIdGenerator;

    /**
     * 获取所有错误指纹统计
     */
    @GetMapping("/fingerprints")
    public List<ErrorFingerprintCache.ErrorFingerprint> getAllFingerprints() {
        log.info("Getting all fingerprints, cache size: {}", fingerprintCache.size());
        List<ErrorFingerprintCache.ErrorFingerprint> fingerprints = fingerprintCache.getAllFingerprints();
        log.info("Returning {} fingerprints", fingerprints.size());

        return fingerprints;
    }

    /**
     * 根据指纹ID获取详细信息
     */
    @GetMapping("/fingerprints/{fingerprint}")
    public ErrorFingerprintCache.ErrorFingerprint getFingerprint(@PathVariable("fingerprint") String fingerprint) {
        ErrorFingerprintCache.ErrorFingerprint fp = fingerprintCache.getFingerprint(fingerprint);

        if (fp != null)
            return fp;
        else
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Error fingerprint not found");
    }

    /**
     * 获取错误统计概览
     */
    @GetMapping("/overview")
    public Map<String, Object> getOverview() {
        List<ErrorFingerprintCache.ErrorFingerprint> fingerprints = fingerprintCache.getAllFingerprints();
        Map<String, Object> overview = new HashMap<>();
        overview.put("totalFingerprints", fingerprints.size());
        overview.put("totalErrors", fingerprints.stream().mapToLong(ErrorFingerprintCache.ErrorFingerprint::getCount).sum());
        overview.put("cacheCapacity", fingerprintCache.size());
        overview.put("timestamp", LocalDateTime.now());

        log.info("Overview: {} fingerprints, {} total errors", fingerprints.size(),
                fingerprints.stream().mapToLong(ErrorFingerprintCache.ErrorFingerprint::getCount).sum());

        // 统计最频繁的错误
        ErrorFingerprintCache.ErrorFingerprint mostFrequent = fingerprints.stream()
                .max((a, b) -> Long.compare(a.getCount(), b.getCount()))
                .orElse(null);

        if (mostFrequent != null) {
            Map<String, Object> mostFrequentInfo = new HashMap<>();
            mostFrequentInfo.put("fingerprint", mostFrequent.getFingerprint());
            mostFrequentInfo.put("count", mostFrequent.getCount());
            overview.put("mostFrequentError", mostFrequentInfo);
        }

        return overview;
    }

    /**
     * 清空错误统计缓存
     */
    @DeleteMapping("/clear")
    public Map<String, String> clearCache() {
        fingerprintCache.clear();
        Map<String, String> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "Error fingerprint cache cleared");

        return result;
    }

    @PostMapping("/simulate/{errorType}")
    public Map<String, String> simulateError(@PathVariable("errorType") String errorType) {
        String traceId = traceIdGenerator.generateTraceId();
        Map<String, String> result = new HashMap<>();

        log.info("Simulating error type: {} with traceId: {}", errorType, traceId);

        try {
            switch (errorType.toLowerCase()) {
                case "npe":
                    simulateNullPointerException();
                    break;
                case "iae":
                    simulateIllegalArgumentException();
                    break;
                case "ioexception":
                    simulateIOException();
                    break;
                default:
                    simulateGenericException();
            }
        } catch (Exception e) {
            log.error("Simulated error occurred for type: " + errorType, e);  // 既记录日志又直接处理，确保数据被添加


            try {  // 直接调用指纹处理，绕过Logback问题
                ErrorFingerprintGenerator generator = new ErrorFingerprintGenerator();
                String fingerprintStr = generator.generateFingerprint(e);
                String stackTrace = getStackTraceString(e);   // 获取堆栈跟踪信息

                ErrorFingerprintCache.getInstance().shouldLog(
                        fingerprintStr, traceId, e.getClass().getSimpleName(), stackTrace);
            } catch (Exception ex) {
                log.warn("Failed to process fingerprint directly: " + ex.getMessage());
            }

            result.put("status", "error_simulated");
            result.put("errorType", e.getClass().getSimpleName());
            result.put("traceId", traceId);
            result.put("message", e.getMessage());
            result.put("cacheSize", String.valueOf(fingerprintCache.size()));

            return result;
        }

        result.put("status", "no_error_occurred");
        result.put("traceId", traceId);
        return result;
    }

    private void simulateNullPointerException() {
        String nullString = null;
        nullString.length(); // 故意触发NPE
    }

    private void simulateIllegalArgumentException() {
        throw new IllegalArgumentException("Invalid argument provided for simulation");
    }

    private void simulateIOException() throws IOException {
        throw new IOException("Simulated IO operation failed");
    }

    private void simulateGenericException() {
        throw new RuntimeException("Generic runtime exception for testing");
    }

    /**
     * 获取异常的堆栈跟踪字符串
     */
    private String getStackTraceString(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);

        return sw.toString();
    }
}