package com.ajaxjs.devtools.jarconflict.model;

import lombok.Data;

import java.util.List;

/**
 * 一次潜在依赖冲突的记录，包含标识、相关依赖、类型、严重程度和修复建议。
 * <p>className 为通用展示标识：类重复时是全限定类名，版本冲突时是库名，
 * JAR 重复时是带前缀的描述。检测器与建议生成器会依次更新此对象。
 */
@Data
public class ConflictInfo {
    /**
     * 规则匹配及页面展示使用的类名、库名或重复 JAR 描述。
     */
    private String className;

    /**
     * 参与此条冲突的依赖列表。
     */
    private List<JarInfo> conflictingJars;

    /**
     * 检测器识别的冲突类型。
     */
    private ConflictType type;

    /**
     * 建议生成器填充的修复说明。
     */
    private String advice;

    /**
     * 检测器初始赋值、建议规则可能覆盖的最终级别。
     */
    private SeverityLevel severity;

    /**
     * 创建检测记录，严重程度和建议由后续处理阶段填充。
     *
     * @param className       类名、库名或重复 JAR 描述
     * @param conflictingJars 涉及的依赖列表，直接保存引用
     * @param type            冲突类型
     */
    public ConflictInfo(String className, List<JarInfo> conflictingJars, ConflictType type) {
        this.className = className;
        this.conflictingJars = conflictingJars;
        this.type = type;
    }
}
