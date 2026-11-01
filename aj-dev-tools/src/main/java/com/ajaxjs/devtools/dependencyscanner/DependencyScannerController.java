package com.ajaxjs.devtools.dependencyscanner;

import com.ajaxjs.devtools.dependencyscanner.model.DependencyInfo;
import com.ajaxjs.devtools.dependencyscanner.model.DependencyRisk;
import com.ajaxjs.devtools.dependencyscanner.model.RiskStatistics;
import com.ajaxjs.devtools.dependencyscanner.service.DependencyCollector;
import com.ajaxjs.devtools.dependencyscanner.service.VulnerabilityMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.*;

/**
 * 提供依赖收集、漏洞扫描、统计、调试和健康检查 REST 接口。
 * <p>统一以 /api 为前缀，每次相关请求重新收集或扫描，没有结果缓存。收集可能
 * 执行 Maven 命令和访问文件系统，在线扫描还需要外部网络查询。
 * <p>vulnerableDependencies 实际使用风险记录数量，不是去重后的依赖数。
 * 调试接口返回本机目录、classpath 和加载器信息。
 */
@Slf4j
@RestController
@RequestMapping("/dependency_scanner")
@RequiredArgsConstructor
public class DependencyScannerController {
    /**
     * 每次调用重新收集候选依赖的组件。
     */
    private final DependencyCollector dependencyCollector;

    /**
     * 漏洞查询、匹配、排序和统计组件。
     */
    private final VulnerabilityMatcher vulnerabilityMatcher;

    /**
     * 扫描依赖并返回风险列表。
     * <p>处理 GET /api/dependencies/scan，重新收集、匹配、排序和统计。响应包含 success、message、
     * data 和 statistics，scanDuration 单位毫秒。vulnerableDependencies 为风险记录数，
     * 一个依赖多个漏洞会重复计数；底层失败可能被转为空结果，success 不保证每项查询成功。
     *
     * @return HTTP 200 及包含 success、message、data、statistics 的响应映射
     */
    @GetMapping("/dependencies/scan")
    public Map<String, Object> scanDependencies() {
        log.info("开始执行依赖扫描...");
        long startTime = System.currentTimeMillis();

        // 1. 收集依赖
        List<DependencyInfo> dependencies = dependencyCollector.collect();
        log.info("收集到 {} 个依赖", dependencies.size());

        // 2. 匹配漏洞
        List<DependencyRisk> risks = vulnerabilityMatcher.matchVulnerabilities(dependencies);

        // 3. 按风险等级排序
        risks = vulnerabilityMatcher.sortByRiskLevel(risks);

        // 4. 获取统计信息
        RiskStatistics statistics = vulnerabilityMatcher.getRiskStatistics(risks);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        log.info("依赖扫描完成，耗时 {} ms，{}", duration, statistics.toString());

        // 构建响应
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "扫描完成");
        response.put("data", risks);

        // 修复统计信息：总依赖数应该是所有依赖，不是只有有漏洞的
        Map<String, Object> statisticsMap = new HashMap<>();
        statisticsMap.put("totalDependencies", dependencies.size());  // 所有依赖数量
        statisticsMap.put("vulnerableDependencies", risks.size());     // 有漏洞的依赖数量
        statisticsMap.put("criticalCount", statistics.criticalCount);
        statisticsMap.put("highCount", statistics.highCount);
        statisticsMap.put("mediumCount", statistics.mediumCount);
        statisticsMap.put("lowCount", statistics.lowCount);
        statisticsMap.put("scanDuration", duration);

        response.put("statistics", statisticsMap);

        return response;
    }

    /**
     * 获取所有依赖列表（不进行漏洞匹配）。
     * <p>处理 GET /api/dependencies，仅收集依赖，不查询漏洞。
     * count 为候选条目数，可能包含本地仓库组件或跨来源重复项。
     *
     * @return 包含 success、message、data 和 count 的 HTTP 200 响应
     */
    @GetMapping("/dependencies")
    public Map<String, Object> getAllDependencies() {
        log.info("获取所有依赖列表...");
        List<DependencyInfo> dependencies = dependencyCollector.collect();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "获取成功");
        response.put("data", dependencies);
        response.put("count", dependencies.size());

        return response;
    }

    /**
     * 获取扫描统计信息。
     * <p>处理 GET /api/statistics，每次重新收集和查询，不使用历史扫描缓存。
     * safePercentage 按收集数减风险记录数计算，多漏洞时可能失真甚至为负；不返回明细。
     *
     * @return 包含 success 及统计 data 的 HTTP 200 响应
     */
    @GetMapping("/statistics")
    public Map<String, Object> getStatistics() {
        log.info("获取扫描统计信息...");

        List<DependencyInfo> dependencies = dependencyCollector.collect();
        List<DependencyRisk> risks = vulnerabilityMatcher.matchVulnerabilities(dependencies);
        RiskStatistics statistics = vulnerabilityMatcher.getRiskStatistics(risks);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "totalDependencies", dependencies.size(),
                "vulnerableDependencies", risks.size(),
                "safePercentage", dependencies.size() > 0 ?
                        Math.round((double) (dependencies.size() - risks.size()) / dependencies.size() * 100) : 100,
                "riskDistribution", Map.of(
                        "critical", statistics.criticalCount,
                        "high", statistics.highCount,
                        "medium", statistics.mediumCount,
                        "low", statistics.lowCount
                )
        ));

        return response;
    }

    /**
     * 调试接口 - 获取详细的依赖收集信息。
     * <p>处理 GET /api/debug/dependencies，返回环境、加载器、classpath 及收集结果。
     * 实际执行收集，可能触发 Maven；响应含用户目录和绝对路径。
     *
     * @return 包含环境、路径和候选依赖信息的 HTTP 200 响应
     */
    @GetMapping("/debug/dependencies")
    public Map<String, Object> debugDependencies() {
        log.info("开始调试依赖收集...");

        Map<String, Object> debugInfo = new HashMap<>();
        // 获取基本环境信息
        debugInfo.put("javaVersion", System.getProperty("java.version"));
        debugInfo.put("javaVendor", System.getProperty("java.vendor"));
        debugInfo.put("userDir", System.getProperty("user.dir"));
        debugInfo.put("userHome", System.getProperty("user.home"));

        // 获取ClassLoader信息
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        debugInfo.put("classLoaderType", classLoader.getClass().getName());
        debugInfo.put("classLoaderString", classLoader.toString());

        // 获取类路径信息
        String classPath = System.getProperty("java.class.path");

        if (classPath != null) {
            String[] paths = classPath.split(File.pathSeparator);
            debugInfo.put("classPathEntries", paths.length);
            debugInfo.put("classPathDetails", Arrays.asList(paths));

            // 统计jar文件数量
            long jarCount = Arrays.stream(paths).filter(path -> path.endsWith(".jar")).count();
            debugInfo.put("jarFilesInClassPath", jarCount);
        }

        // 尝试收集依赖并获取详细信息
        List<DependencyInfo> dependencies = dependencyCollector.collect();
        debugInfo.put("collectedDependencies", dependencies.size());

        // 添加依赖详情
        List<Map<String, Object>> dependencyDetails = new ArrayList<>();
        for (DependencyInfo dep : dependencies) {
            Map<String, Object> depInfo = new HashMap<>();
            depInfo.put("groupId", dep.getGroupId());
            depInfo.put("artifactId", dep.getArtifactId());
            depInfo.put("version", dep.getVersion());
            depInfo.put("jarPath", dep.getJarPath());
            dependencyDetails.add(depInfo);
        }

        debugInfo.put("dependencyDetails", dependencyDetails);

        // 检查Maven相关目录
        File targetDir = new File(System.getProperty("user.dir"), "target");
        debugInfo.put("targetDirExists", targetDir.exists());

        if (targetDir.exists()) {
            File[] targetFiles = targetDir.listFiles();

            if (targetFiles != null) {
                debugInfo.put("targetDirContents", Arrays.stream(targetFiles)
                        .map(File::getName)
                        .collect(java.util.stream.Collectors.toList()));
            }
        }

        debugInfo.put("success", true);

        return debugInfo;
    }
}