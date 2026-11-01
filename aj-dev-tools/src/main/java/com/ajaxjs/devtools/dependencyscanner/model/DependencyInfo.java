package com.ajaxjs.devtools.dependencyscanner.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 候选依赖的坐标和发现来源。
 * <p>坐标可能由 Manifest 或文件名推断，unknown 表示未识别。jarPath 可能是路径，
 * 也可能是 maven-tree 等来源标记，不保证能打开对应文件。
 * Lombok 生成访问器以及基于全部字段的 equals、hashCode 和 toString。
 */
@Data
@AllArgsConstructor
public class DependencyInfo {
    /**
     * 组件组名，可能来自坐标解析或启发式推断。
     */
    private String groupId;

    /**
     * 组件名称，未必能与原始 Maven 坐标完全对应。
     */
    private String artifactId;

    /**
     * 依赖的当前版本，范围工具的公开方法允许 null,未识别时可能为 unknown。
     */
    private String version;

    /**
     * 归档路径或 maven-tree、maven-list 等来源标记。
     */
    private String jarPath;

    /**
     * 检查当前依赖是否匹配指定的漏洞。
     * <p>仅精确比较 groupId 与 artifactId，区分大小写，不检查版本，也不采用模糊规则。
     *
     * @param vulnerability 待读取、写入或填充的非空漏洞模型
     * @return 两个坐标字段精确相等时为 true
     */
    public boolean matches(Vulnerability vulnerability) {
        return groupId.equals(vulnerability.getGroupId()) && artifactId.equals(vulnerability.getArtifactId());
    }
}