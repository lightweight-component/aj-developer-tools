package com.ajaxjs.devtools.jarconflict;

import com.ajaxjs.devtools.jarconflict.advisor.ConflictAdvisor;
import com.ajaxjs.devtools.jarconflict.advisor.ConflictDetector;
import com.ajaxjs.devtools.jarconflict.model.ConflictInfo;
import com.ajaxjs.devtools.jarconflict.model.JarInfo;
import com.ajaxjs.devtools.jarconflict.model.ScanResult;
import com.ajaxjs.devtools.jarconflict.model.ScanSummary;
import com.ajaxjs.devtools.jarconflict.scanner.JarScanner;
import com.ajaxjs.devtools.jarconflict.scanner.TestConflictDataGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提供真实扫描、模拟扫描、依赖清单、冲突明细和健康检查接口。
 * <p>每次扫描请求同步重新计算，没有结果缓存。完整报告依次执行扫描、冲突检测、
 * 建议生成和统计汇总。响应包含依赖路径及类清单，供静态报告页面展示。
 */
@Slf4j
@RestController
@RequestMapping("/jar_conflict")
@RequiredArgsConstructor
public class ScanController {
    private final JarScanner jarScanner;

    private final ConflictDetector conflictDetector;

    private final ConflictAdvisor conflictAdvisor;

    private final TestConflictDataGenerator testDataGenerator;

    /**
     * 使用模拟数据检测冲突并生成建议，不访问真实 classpath。
     *
     * @return HTTP 200 及 test 模式报告
     */
    @GetMapping("/test-scan")
    public ScanResult testScan() {
        log.info("Starting test jar conflict scan with simulated data...");
        long startTime = System.currentTimeMillis();

        List<JarInfo> jars = testDataGenerator.generateTestConflictData();// 使用模拟的冲突数据进行测试
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
        return result;
    }

    /**
     * 同步扫描真实 classpath，检测冲突并生成建议。
     *
     * @return HTTP 200 及完整报告
     */
    @GetMapping("/scan")
    public ScanResult scan() {
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
        return result;
    }

    /**
     * 重新扫描 classpath，只返回依赖清单。
     *
     * @return HTTP 200 及依赖列表
     */
    @GetMapping("/jars")
    public List<JarInfo> getJars() {
        return jarScanner.scanJars();
    }

    /**
     * 重新扫描、检测并生成建议，只返回冲突明细。
     *
     * @return HTTP 200 及冲突列表
     */
    @GetMapping("/conflicts")
    public List<ConflictInfo> getConflicts() {
        List<JarInfo> jars = jarScanner.scanJars();
        List<ConflictInfo> conflicts = conflictDetector.detectConflicts(jars);
        conflictAdvisor.generateAdvice(conflicts);

        return conflicts;
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
