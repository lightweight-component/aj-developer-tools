package com.ajaxjs.devtools.jarconflict.model;

import lombok.Data;

import java.util.List;

/**
 * 一条独立严重程度规则，对应 {@code conflict.advisor.severity-rules} 的列表元素。
 * <p>类型、最小 JAR 数和模式三类条件之间为 AND 关系，未配置的条件不限制匹配。
 * 多个模式之间为 OR 关系。建议生成器按列表顺序取首条匹配规则，
 * 且只在冲突的严重程度尚未赋值时调用此类。
 */
@Data
public class SeverityRule {
    /**
     * 可选正则列表，任一模式匹配即可满足模式条件。
     */
    private List<String> patterns;

    /**
     * 规则命中后使用的级别；空值或非法文本解析为 LOW。
     */
    private String severity; // 改为String类型

    /**
     * 可选最小 JAR 数量，包含边界。
     */
    private Integer minJarCount;

    /**
     * 可选冲突类型名称列表，使用枚举名称且区分大小写。
     */
    private List<String> conflictTypes; // 改为String类型

    /**
     * 检查全部已配置的类型、数量和模式条件。
     * <p>类型使用区分大小写的枚举名称比较，数量阈值包含边界。
     * 模式按小写后的整串正则匹配；未配置模式时只检查其余条件。
     *
     * @param identifier   非空冲突标识
     * @param jarCount     涉及的 JAR 数量
     * @param conflictType 冲突类型的枚举名称
     * @return 所有已配置条件均满足时为 true
     */
    public boolean matches(String identifier, int jarCount, String conflictType) {
        if (conflictTypes != null && !conflictTypes.isEmpty() && !conflictTypes.contains(conflictType)) // 检查冲突类型
            return false;

        if (minJarCount != null && jarCount < minJarCount) // 检查Jar数量
            return false;

        if (patterns != null && !patterns.isEmpty()) {  // 检查模式匹配
            String lowerInput = identifier.toLowerCase();

            return patterns.stream().anyMatch(pattern -> lowerInput.matches(pattern.toLowerCase()));
        }

        return true;
    }
}