package com.ajaxjs.devtools.hotcpu;

import com.ajaxjs.devtools.hotcpu.model.vo.ClearResult;
import com.ajaxjs.devtools.hotcpu.model.vo.DebugInfo;
import com.ajaxjs.devtools.hotcpu.model.vo.HealthStatus;
import com.ajaxjs.devtools.hotcpu.model.vo.SamplingOperation;
import com.ajaxjs.devtools.hotcpu.model.vo.SamplingStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
@Slf4j
public class FlameGraphController {
    @Autowired
    private CpuSampler sampler;

    @GetMapping(value = "/flamegraph", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getFlameGraphData() {
        StringBuilder sb = new StringBuilder();
        Map<String, AtomicInteger> stackCount = sampler.getStackCount();

        if (stackCount.isEmpty())
            return "# No sampling data available. Make sure sampling is enabled.\n";

        stackCount.forEach((stack, count) -> {
            if (stack != null && !stack.trim().isEmpty() && count.get() > 0) {
                String cleanStack = stack.endsWith(";") ? stack.substring(0, stack.length() - 1) : stack;
                sb.append(cleanStack).append(" ").append(count.get()).append("\n");
            }
        });

        log.debug("Generated flame graph data with {} entries", stackCount.size());
        return sb.toString();
    }

    @PostMapping("/sampling/enable")
    public SamplingOperation enableSampling() {
        sampler.enableSampling();
        SamplingOperation response = new SamplingOperation();
        response.setStatus("enabled");
        response.setMessage("CPU sampling has been enabled");
        log.info("CPU sampling enabled via API");

        return response;
    }

    @PostMapping("/sampling/disable")
    public SamplingOperation disableSampling() {
        sampler.disableSampling();
        SamplingOperation response = new SamplingOperation();
        response.setStatus("disabled");
        response.setMessage("CPU sampling has been disabled");
        log.info("CPU sampling disabled via API");

        return response;
    }

    @GetMapping("/sampling/status")
    public SamplingStatus getSamplingStatus() {
        SamplingStatus response = new SamplingStatus();
        response.setEnabled(sampler.isEnabled());
        response.setStackCountSize(sampler.getStackCountSize());

        return response;
    }

    @PostMapping("/sampling/clear")
    public ClearResult clearSamplingData() {
        sampler.clearData();
        ClearResult response = new ClearResult();
        response.setMessage("Sampling data cleared successfully");
        log.info("Sampling data cleared via API");

        return response;
    }

    @GetMapping("/sampling/debug")
    public DebugInfo debugSampling() {
        DebugInfo response = new DebugInfo();
        response.setEnabled(sampler.isEnabled());
        response.setStackCountSize(sampler.getStackCountSize());

        Map<String, AtomicInteger> stackCount = sampler.getStackCount();
        Map<String, Integer> sampleData = new HashMap<>();
        int count = 0;

        for (Map.Entry<String, AtomicInteger> entry : stackCount.entrySet()) {
            if (count >= 10) break;
            sampleData.put(entry.getKey(), entry.getValue().get());
            count++;
        }

        response.setSampleData(sampleData);

        return response;
    }

    @GetMapping("/health")
    public HealthStatus health() {
        HealthStatus response = new HealthStatus();
        response.setStatus("UP");
        response.setService("springboot-hot-cpu");
        response.setSampling(sampler.isEnabled() ? "enabled" : "disabled");

        return response;
    }
}