package com.ajaxjs.devtools.hotcpu.controller;


import com.ajaxjs.devtools.hotcpu.service.CpuSampler;
import com.ajaxjs.framework.mvc.unifiedreturn.PureOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class FlameGraphController {
    @Autowired
    private CpuSampler sampler;

    @PureOutput
    @GetMapping(value = "/flamegraph", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getFlameGraphData() {
        StringBuilder sb = new StringBuilder();

        try {
            Map<String, AtomicInteger> stackCount = sampler.getStackCount();

            if (stackCount.isEmpty())
                return ResponseEntity.ok("# No sampling data available. Make sure sampling is enabled.\n");

            stackCount.forEach((stack, count) -> {
                if (stack != null && !stack.trim().isEmpty() && count.get() > 0) {
                    // 移除末尾的分号
                    String cleanStack = stack.endsWith(";") ? stack.substring(0, stack.length() - 1) : stack;
                    sb.append(cleanStack).append(" ").append(count.get()).append("\n");
                }
            });

            log.debug("Generated flame graph data with {} entries", stackCount.size());
            return ResponseEntity.ok(sb.toString());
        } catch (Exception e) {
            log.error("Error generating flame graph data", e);
            return ResponseEntity.internalServerError().body("Error generating flame graph data: " + e.getMessage());
        }
    }

    @PostMapping("/sampling/enable")
    public ResponseEntity<Map<String, Object>> enableSampling() {
        sampler.enableSampling();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "enabled");
        response.put("message", "CPU sampling has been enabled");
        log.info("CPU sampling enabled via API");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/sampling/disable")
    public ResponseEntity<Map<String, Object>> disableSampling() {
        sampler.disableSampling();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "disabled");
        response.put("message", "CPU sampling has been disabled");
        log.info("CPU sampling disabled via API");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/sampling/status")
    public ResponseEntity<Map<String, Object>> getSamplingStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("enabled", sampler.isEnabled());
        response.put("stackCountSize", sampler.getStackCountSize());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/sampling/clear")
    public ResponseEntity<Map<String, Object>> clearSamplingData() {
        sampler.clearData();
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Sampling data cleared successfully");
        log.info("Sampling data cleared via API");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/sampling/debug")
    public ResponseEntity<Map<String, Object>> debugSampling() {
        Map<String, Object> response = new HashMap<>();
        response.put("enabled", sampler.isEnabled());
        response.put("stackCountSize", sampler.getStackCountSize());

        // 获取前10条数据作为调试信息
        Map<String, AtomicInteger> stackCount = sampler.getStackCount();
        Map<String, Integer> sampleData = new HashMap<>();
        int count = 0;

        for (Map.Entry<String, AtomicInteger> entry : stackCount.entrySet()) {
            if (count >= 10) break;
            sampleData.put(entry.getKey(), entry.getValue().get());
            count++;
        }

        response.put("sampleData", sampleData);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "springboot-hot-cpu");
        response.put("sampling", sampler.isEnabled() ? "enabled" : "disabled");

        return ResponseEntity.ok(response);
    }
}