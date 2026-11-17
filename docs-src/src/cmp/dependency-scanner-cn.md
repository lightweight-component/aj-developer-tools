---
title: 依赖包漏洞扫描
subTitle: 一键发现潜在安全漏洞
description: 基本概念
tags:
  - 基本概念
layout: layouts/docs-cn.njk
---

# 依赖包漏洞扫描
<!-- https://juejin.cn/post/7541741121400045578 -->


在日常项目开发中，依赖第三方库几乎是不可避免的。从 `spring-boot-starter` 到 MyBatis、Log4j，再到各种工具类库，几乎每个应用都需要几十甚至上百个依赖。然而，这些依赖并非完全安全。很多开发者可能在不知情的情况下，使用了存在 **已知漏洞** 的版本，轻则引发应用崩溃，重则导致远程代码执行或数据泄漏。

本文带你实现一个依赖库漏洞在线扫描仪，包含以下内容：

1. 执行后自动扫描所有依赖 jar 包
2. 匹配漏洞数据库，检测已知漏洞
3. 将扫描结果可视化展示，直观告诉你哪个依赖存在漏洞、漏洞危害描述、对应的 CVE 编号等信息

![image.png](https://p3-xtjj-sign.byteimg.com/tos-cn-i-73owjymdk6/89c0dfa226b84bfc80dfc8d055de68d2~tplv-73owjymdk6-jj-mark-v1:0:0:0:0:5o6Y6YeR5oqA5pyv56S-5Yy6IEAg6ZmI56u55LiD:q75.awebp?rk3s=f64ab15b&x-expires=1793577100&x-signature=EMpgcanH%2BYg7hiwgqwkuG5yWGGg%3D)

![image.png](https://p3-xtjj-sign.byteimg.com/tos-cn-i-73owjymdk6/7a5602ad99d5424bad9f4d4c757b24a1~tplv-73owjymdk6-jj-mark-v1:0:0:0:0:5o6Y6YeR5oqA5pyv56S-5Yy6IEAg6ZmI56u55LiD:q75.awebp?rk3s=f64ab15b&x-expires=1793577100&x-signature=2PPfJtNrPwTbEVaJGxXYC5laMPY%3D)



## 现状分析

目前常见方案：

- **OWASP Dependency-Check**：功能强大，但集成较重，国内文档少。
- **Snyk / WhiteSource**：商业化产品，部分功能需付费。

所以我们希望在 SpringBoot 应用内部，做一个 **轻量级依赖漏洞扫描仪**，即插即用，足够简单。

## 解决思路

1. **扫描依赖**：读取当前应用的所有 jar 包版本。 
1. **匹配漏洞库**：根据 groupId/artifactId/version 判断是否命中漏洞。 
1. **输出漏洞详情**：包括 - CVE 编号 - 漏洞描述 - 危害等级（CVSS） - 修复方案（安全版本范围） - 官方参考链接
1. **前端可视化**：表格展示结果，按严重性排序，高危一目了然。


## 系统设计

- **依赖收集器**：获取所有依赖及版本信息
- **漏洞在线扫描**：调用相关漏洞查询库完成漏洞分析
- **漏洞匹配器**：判断依赖版本是否落在漏洞范围
 

##  关键实现

####  漏洞数据结构

定义一个漏洞实体，包含详细说明与修复方案：

```java
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

```json
[{
    "groupId": "org.apache.logging.log4j",    "artifactId": "log4j-core",    "vulnerableVersions": "<=2.14.1",    "safeVersion": "2.15.0+",    "cve": "CVE-2021-44228",    "description": "Log4j 2 JNDI 远程代码执行漏洞，攻击者可通过恶意日志消息执行任意代码。",    "severity": "Critical",    "reference": "https://nvd.nist.gov/vuln/detail/CVE-2021-44228"  
}]
```
 

**红色标记高危漏洞** 一行信息清楚展现漏洞 + 修复方案，可点击链接查看官方公告

 
## 实战应用场景

- **本地开发自查**  开发者在调试时即可运行扫描，第一时间发现依赖风险。
- **应急响应**  当新漏洞爆出（如 Log4Shell），立刻扫描项目，快速定位受影响模块，并知道该升到哪个版本。
- **CI/CD 集成**  在构建流水线中调用 `/dependencies` API：若发现高危漏洞 → 直接 fail 构建；提示需升级到安全版本后，才能继续部署