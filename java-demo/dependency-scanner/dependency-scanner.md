https://juejin.cn/post/7541741121400045578


在日常项目开发中，依赖第三方库几乎是不可避免的。从 `spring-boot-starter` 到 MyBatis、Log4j，再到各种工具类库，几乎每个应用都需要几十甚至上百个依赖。然而，这些依赖并非完全安全。很多开发者可能在不知情的情况下，使用了存在 **已知漏洞** 的版本，轻则引发应用崩溃，重则导致远程代码执行或数据泄漏。

本文带你实现一个依赖库漏洞在线扫描仪，包含以下内容：

1. 执行后自动扫描所有依赖 jar 包
2. 匹配漏洞数据库，检测已知漏洞
3. 将扫描结果 可视化展示，直观告诉你哪个依赖存在漏洞、漏洞危害描述、对应的 CVE 编号等信息

![image.png](https://p3-xtjj-sign.byteimg.com/tos-cn-i-73owjymdk6/89c0dfa226b84bfc80dfc8d055de68d2~tplv-73owjymdk6-jj-mark-v1:0:0:0:0:5o6Y6YeR5oqA5pyv56S-5Yy6IEAg6ZmI56u55LiD:q75.awebp?rk3s=f64ab15b&x-expires=1793577100&x-signature=EMpgcanH%2BYg7hiwgqwkuG5yWGGg%3D)

![image.png](https://p3-xtjj-sign.byteimg.com/tos-cn-i-73owjymdk6/7a5602ad99d5424bad9f4d4c757b24a1~tplv-73owjymdk6-jj-mark-v1:0:0:0:0:5o6Y6YeR5oqA5pyv56S-5Yy6IEAg6ZmI56u55LiD:q75.awebp?rk3s=f64ab15b&x-expires=1793577100&x-signature=2PPfJtNrPwTbEVaJGxXYC5laMPY%3D)

## 1\. 需求痛点

**1\. 依赖数量庞大**：一个典型的 Spring Boot 应用常常包含 100+ 依赖，人工排查几乎不可能。

**2\. 漏洞信息分散**：CVE 公告、NVD、GitHub issue，开发者很难第一时间获取。

**3\. 缺乏修复指导**：即使知道某个版本有漏洞，很多团队也不清楚应该升级到哪个安全版本。

因此，一个 **一键扫描 + 漏洞说明 + 修复建议** 的工具，能够极大提升团队安全响应效率。

## 2\. 现状分析

目前常见方案：

**OWASP Dependency-Check**：功能强大，但集成较重，国内文档少。

**Snyk / WhiteSource**：商业化产品，部分功能需付费。

**手工排查**：低效且容易遗漏。

所以，我们希望在 SpringBoot 应用内部，做一个 **轻量级依赖漏洞扫描仪**，即插即用，足够简单。

## 3\. 解决思路

**1\. 扫描依赖**：读取当前应用的所有 jar 包版本。 **2\. 匹配漏洞库**：根据 groupId/artifactId/version 判断是否命中漏洞。 **3\. 输出漏洞详情**：包括 - CVE 编号 - 漏洞描述 - 危害等级（CVSS） - 修复方案（安全版本范围） - 官方参考链接

**4\. 前端可视化**：表格展示结果，按严重性排序，高危一目了然。

___

## 4\. 系统设计

**依赖收集器**：获取所有依赖及版本信息

**漏洞在线扫描**：调用相关漏洞查询库完成漏洞分析

**漏洞匹配器**：判断依赖版本是否落在漏洞范围

**结果展示层**：Spring Boot REST API + 前端表格

## 5\. 关键实现

#### 5.1 漏洞数据结构

定义一个漏洞实体，包含详细说明与修复方案：

```
public class Vulnerability {
    private String cve;
    private String description;
    private String severity;
    private String vulnerableVersions;
    private String safeVersion;
    private String reference;

    public boolean isVersionVulnerable(String version) {
        // 可用 Maven ArtifactVersion 进行范围匹配
        return VersionRangeChecker.isVulnerable(version, vulnerableVersions);
    }
}
```

示例漏洞数据（JSON 文件）：

```
[  {    "groupId": "org.apache.logging.log4j",    "artifactId": "log4j-core",    "vulnerableVersions": "<=2.14.1",    "safeVersion": "2.15.0+",    "cve": "CVE-2021-44228",    "description": "Log4j 2 JNDI 远程代码执行漏洞，攻击者可通过恶意日志消息执行任意代码。",    "severity": "Critical",    "reference": "https://nvd.nist.gov/vuln/detail/CVE-2021-44228"  }]
```

#### 5.2 扫描依赖并匹配漏洞

```
@GetMapping("/dependencies/scan")
public ResponseEntity<Map<String, Object>> scanDependencies() {
    logger.info("开始执行依赖扫描...");
    
    long startTime = System.currentTimeMillis();
    
    try {
        // 1. 收集依赖
        List<DependencyInfo> dependencies = dependencyCollector.collect();
        logger.info("收集到 {} 个依赖", dependencies.size());
        
        // 2. 匹配漏洞
        List<DependencyRisk> risks = vulnerabilityMatcher.matchVulnerabilities(dependencies);
        
        // 3. 按风险等级排序
        risks = vulnerabilityMatcher.sortByRiskLevel(risks);
        
        // 4. 获取统计信息
        VulnerabilityMatcher.RiskStatistics statistics = vulnerabilityMatcher.getRiskStatistics(risks);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        logger.info("依赖扫描完成，耗时 {} ms，{}", duration, statistics.toString());
        
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
        
        return ResponseEntity.ok(response);
        
    } catch (Exception e) {
        logger.error("依赖扫描失败", e);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("message", "扫描失败: " + e.getMessage());
        errorResponse.put("data", List.of());
        
        return ResponseEntity.status(500).body(errorResponse);
    }
}
```

返回的 JSON 示例：

```json
[  {    "groupId": "org.apache.logging.log4j",    "artifactId": "log4j-core",    "version": "2.14.1",    "riskLevel": "Critical",    "cve": "CVE-2021-44228",    "description": "Log4j JNDI 注入可导致远程代码执行。",    "safeVersion": "2.15.0+",    "reference": "https://nvd.nist.gov/vuln/detail/CVE-2021-44228"  }]
```
 

**红色标记高危漏洞**

-   一行信息清楚展现漏洞 + 修复方案
-   可点击链接查看官方公告

___

## 7\. 实战应用场景

**1\. 本地开发自查**  
开发者在调试时即可运行扫描，第一时间发现依赖风险。

**2\. 应急响应**  
当新漏洞爆出（如 Log4Shell），立刻扫描项目，快速定位受影响模块，并知道该升到哪个版本。

**3\. CI/CD 集成**  
在构建流水线中调用 `/dependencies` API：

-   若发现高危漏洞 → 直接 fail 构建
-   提示需升级到安全版本后，才能继续部署

## 8\. 总结

本文实现了一个轻量级的 SpringBoot 在线「依赖包漏洞扫描仪」：

**自动收集依赖**，无需人工干预

**匹配漏洞库**，输出 CVE 编号 + 漏洞描述

**提供漏洞详情信息**，避免知道有问题但不知如何解决

