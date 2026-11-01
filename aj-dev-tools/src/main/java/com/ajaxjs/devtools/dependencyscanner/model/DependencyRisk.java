package com.ajaxjs.devtools.dependencyscanner.model;

import lombok.Data;

/**
 * 具体依赖版本关联的一条漏洞风险，用于扫描接口响应。
 * <p>构造时复制依赖和漏洞字段，不再执行坐标匹配、版本校验或远程查询。
 * 一个依赖可对应多条漏洞记录，因此风险记录数量不同于受影响依赖的去重数量。
 */
@Data
public class DependencyRisk {
    /**
     * 组件组名，可能来自坐标解析或启发式推断。
     */
    private String groupId;

    /**
     * 组件名称，未必能与原始 Maven 坐标完全对应。
     */
    private String artifactId;

    /**
     * 依赖的当前版本，未识别时可能为 unknown。
     */
    private String version;

    /**
     * 风险级别，通常为 CRITICAL、HIGH、MEDIUM 或 LOW。
     */
    private String riskLevel;

    /**
     * 漏洞标识，可能为 CVE、OSV ID 或 UNKNOWN。
     */
    private String cve;

    /**
     * 漏洞说明或展示摘要。
     */
    private String description;

    /**
     * 修复版本、版本表达式或提示文本，不保证适用于当前版本分支。
     */
    private String safeVersion;

    /**
     * 受影响版本表达式；OSV 转换后的值可能未完整保留原始区间。
     */
    private String vulnerableVersions;  // 新增：影响版本范围

    /**
     * 参考链接文本，模型不校验协议。
     */
    private String reference;

    /**
     * 将依赖的坐标及版本与漏洞的描述字段组合，不再次校验匹配关系。
     *
     * @param dependency    待查询或匹配的依赖，坐标和版本应有效
     * @param vulnerability 待读取、写入或填充的非空漏洞模型
     */
    public DependencyRisk(DependencyInfo dependency, Vulnerability vulnerability) {
        this.groupId = dependency.getGroupId();
        this.artifactId = dependency.getArtifactId();
        this.version = dependency.getVersion();
        this.riskLevel = vulnerability.getSeverity();
        this.cve = vulnerability.getCve();
        this.description = vulnerability.getDescription();
        this.safeVersion = vulnerability.getSafeVersion();
        this.vulnerableVersions = vulnerability.getVulnerableVersions(); // 新增
        this.reference = vulnerability.getReference();
    }
}