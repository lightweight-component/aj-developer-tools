package com.example.jarconflict.controller;

import com.example.jarconflict.advisor.ConflictAdvisor;
import com.example.jarconflict.advisor.ConflictDetector;
import com.example.jarconflict.model.ConflictInfo;
import com.example.jarconflict.model.JarInfo;
import com.example.jarconflict.model.ScanResult;
import com.example.jarconflict.model.ScanSummary;
import com.example.jarconflict.scanner.JarScanner;
import com.example.jarconflict.scanner.TestConflictDataGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 提供真实扫描、模拟扫描、依赖清单、冲突明细和健康检查接口。
 * <p>每次扫描请求同步重新计算，没有结果缓存。完整报告依次执行扫描、冲突检测、
 * 建议生成和统计汇总。响应包含依赖路径及类清单，供静态报告页面展示。
 */
@Controller
@RequestMapping("/")
@Slf4j
public class ScanController {
    @Autowired
    private JarScanner jarScanner;

    @Autowired
    private ConflictDetector conflictDetector;

    @Autowired
    private ConflictAdvisor conflictAdvisor;

    @Autowired
    private TestConflictDataGenerator testDataGenerator;

    /**
     * 使用模拟数据检测冲突并生成建议，不访问真实 classpath。
     *
     * @return HTTP 200 及 test 模式报告
     */
    @GetMapping("/api/test-scan")
    @ResponseBody
    public ResponseEntity<ScanResult> testScan() {
        log.info("Starting test jar conflict scan with simulated data...");
        long startTime = System.currentTimeMillis();

        // 使用模拟的冲突数据进行测试
        List<JarInfo> jars = testDataGenerator.generateTestConflictData();
        List<ConflictInfo> conflicts = conflictDetector.detectConflicts(jars);
        conflictAdvisor.generateAdvice(conflicts);

        long scanTime = System.currentTimeMillis() - startTime;

        ScanResult result = new ScanResult();
        result.setJars(jars);
        result.setConflicts(conflicts);
        result.setScanTimeMs(scanTime);
        result.setScanMode("test");
        result.setSummary(buildSummary(jars, conflicts));

        log.info("Test scan completed in {}ms, found {} conflicts", scanTime, conflicts.size());
        return ResponseEntity.ok(result);
    }

    /**
     * 将根路径请求重定向到静态报告页面。
     *
     * @return Spring MVC 重定向视图名
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/index.html";
    }

    /**
     * 同步扫描真实 classpath，检测冲突并生成建议。
     *
     * @return HTTP 200 及完整报告
     */
    @GetMapping("/api/scan")
    @ResponseBody
    public ResponseEntity<ScanResult> scan() {
        log.info("Starting jar conflict scan...");
        long startTime = System.currentTimeMillis();

        List<JarInfo> jars = jarScanner.scanJars();
        List<ConflictInfo> conflicts = conflictDetector.detectConflicts(jars);
        conflictAdvisor.generateAdvice(conflicts);

        long scanTime = System.currentTimeMillis() - startTime;

        ScanResult result = new ScanResult();
        result.setJars(jars);
        result.setConflicts(conflicts);
        result.setScanTimeMs(scanTime);
        result.setScanMode("auto");
        result.setSummary(buildSummary(jars, conflicts));

        log.info("Scan completed in {}ms, found {} conflicts", scanTime, conflicts.size());
        return ResponseEntity.ok(result);
    }

    /**
     * 重新扫描 classpath，只返回依赖清单。
     *
     * @return HTTP 200 及依赖列表
     */
    @GetMapping("/api/jars")
    @ResponseBody
    public ResponseEntity<List<JarInfo>> getJars() {
        List<JarInfo> jars = jarScanner.scanJars();
        return ResponseEntity.ok(jars);
    }

    /**
     * 重新扫描、检测并生成建议，只返回冲突明细。
     *
     * @return HTTP 200 及冲突列表
     */
    @GetMapping("/api/conflicts")
    @ResponseBody
    public ResponseEntity<List<ConflictInfo>> getConflicts() {
        List<JarInfo> jars = jarScanner.scanJars();
        List<ConflictInfo> conflicts = conflictDetector.detectConflicts(jars);
        conflictAdvisor.generateAdvice(conflicts);

        return ResponseEntity.ok(conflicts);
    }

    /**
     * 返回固定健康状态，不验证扫描功能或依赖可用性。
     *
     * @return HTTP 200 及状态文本
     */
    @GetMapping("/api/health")
    @ResponseBody
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"UP\"}");
    }

    /**
     * 汇总依赖数、类条目数和最终严重程度分布，重复类不去重。
     *
     * @param jars      扫描依赖
     * @param conflicts 已设置严重程度的冲突
     * @return 聚合统计
     */
    private ScanSummary buildSummary(List<JarInfo> jars, List<ConflictInfo> conflicts) {
        ScanSummary summary = new ScanSummary();
        summary.setTotalJars(jars.size());
        summary.setTotalClasses(jars.stream()
                .mapToInt(jar -> jar.getClasses() != null ? jar.getClasses().size() : 0)
                .sum());
        summary.setConflictCount(conflicts.size());

        int critical = 0, high = 0, medium = 0, low = 0;

        for (ConflictInfo conflict : conflicts) {
            switch (conflict.getSeverity()) {
                case CRITICAL -> critical++;
                case HIGH -> high++;
                case MEDIUM -> medium++;
                case LOW -> low++;
            }
        }

        summary.setCriticalConflicts(critical);
        summary.setHighConflicts(high);
        summary.setMediumConflicts(medium);
        summary.setLowConflicts(low);

        return summary;
    }
}