package com.ajaxjs.devtools.jarconflict.advisor;

import com.ajaxjs.devtools.jarconflict.model.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 根据配置规则为已检测出的冲突生成建议，并补充或覆盖严重程度。
 * <p>通过 {@code conflict.advisor} 前缀绑定配置。建议规则先按标识匹配，命中后使用
 * 该规则的模板和可选严重程度；未命中时使用默认模板。仅当严重程度仍为空时，
 * 才评估独立的严重程度规则。传入的冲突对象会被直接修改。
 * <p>建议规则保存在 Map 中，没有显式优先级；重叠规则不应依赖 YAML 书写顺序。
 * 本组件不负责判断类是否重复，也不分析字节码兼容性。
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "devtools.jarconflict.advisor")
public class ConflictAdvisor {
    /**
     * 建议规则映射；键为规则名称，没有显式匹配优先级。
     */
    @Getter
    @Setter
    private Map<String, RuleDefinition> rules = new HashMap<>();

    /**
     * 独立严重程度规则，按列表顺序匹配。
     */
    @Getter
    @Setter
    private List<SeverityRule> severityRules = new ArrayList<>();

    /**
     * 未命中建议规则时使用的默认模板。
     */
    @Getter
    @Setter
    private String defaultAdvice = "检测到依赖冲突。建议：\n" +
            "1. 使用 mvn dependency:tree 分析依赖关系\n" +
            "2. 使用 <exclusion> 排除冲突依赖\n" +
            "3. 在dependencyManagement中统一版本管理\n" +
            "4. 优先使用Spring Boot的版本管理";

    /**
     * 批量生成建议，直接更新冲突对象。建议规则的严重程度可以覆盖已有值。
     * <p>仅在严重程度仍为 null 时才评估 severity-rules；检测器通常已经赋值，
     * 所以正常扫描流程中独立严重程度规则通常不会执行。
     *
     * @param conflicts 已完成检测的非空列表，元素及其标识、类型和依赖列表应有效
     */
    public void generateAdvice(List<ConflictInfo> conflicts) {
        log.debug("=== ConflictAdvisor Debug Info ===");
        log.debug("Loaded rules: {}", rules.size());
        log.debug("Loaded severity rules: {}", severityRules.size());

        for (ConflictInfo conflict : conflicts) {
            String advice = generateAdviceForConflict(conflict);
            conflict.setAdvice(advice);

            if (conflict.getSeverity() == null)   // 评估严重程度
                conflict.setSeverity(evaluateSeverity(conflict));
        }
    }

    /**
     * 取首条命中的建议规则并格式化；未命中时使用默认模板。
     *
     * @param conflict 待处理的冲突
     * @return 格式化后的建议文本
     */
    private String generateAdviceForConflict(ConflictInfo conflict) {
        String identifier = extractIdentifier(conflict);
        log.debug("Processing conflict: {}", identifier);

        for (Map.Entry<String, RuleDefinition> entry : rules.entrySet()) {// 查找匹配的规则
            RuleDefinition rule = entry.getValue();

            if (rule.matches(identifier)) {
                log.debug("Matched rule: {}", entry.getKey());

                if (rule.getSeverity() != null)   // 设置匹配到的严重程度
                    conflict.setSeverity(parseSeverity(rule.getSeverity()));

                return formatAdvice(rule.getAdvice(), conflict);
            }
        }

        return formatAdvice(defaultAdvice, conflict);
    }

    /**
     * 提取匹配标识。当前所有类型均直接使用 className 字段。
     *
     * @param conflict 待匹配的冲突
     * @return 类名、库名或重复 JAR 描述
     */
    private String extractIdentifier(ConflictInfo conflict) {
        return switch (conflict.getType()) {
            case CLASS_DUPLICATE -> conflict.getClassName();
            case VERSION_CONFLICT, JAR_DUPLICATE -> conflict.getClassName();
        };
    }

    /**
     * 对模板执行字面量变量替换，不解释表达式，也不进行 HTML 转义。
     * <p>未识别的占位符保留原样；模板不能为空。
     *
     * @param template 建议模板
     * @param conflict 变量数据来源
     * @return 替换后的建议
     */
    private String formatAdvice(String template, ConflictInfo conflict) {
        Map<String, String> variables = buildVariables(conflict);
        String result = template;

        for (Map.Entry<String, String> entry : variables.entrySet())
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());

        return result;
    }

    /**
     * 构建 className、conflictType、jarCount、jars、jarList、versions 和 paths 变量。
     * <p>versions 排除空值和 unknown 后去重；jars 以逗号分隔，jarList 和 paths 以换行分隔。
     *
     * @param conflict 包含标识和依赖信息的冲突
     * @return 模板变量映射
     */
    private Map<String, String> buildVariables(ConflictInfo conflict) {
        Map<String, String> variables = new HashMap<>();
        variables.put("className", conflict.getClassName());
        variables.put("conflictType", getConflictTypeText(conflict.getType()));
        variables.put("jarCount", String.valueOf(conflict.getConflictingJars().size()));

        List<String> jarNames = conflict.getConflictingJars().stream()
                .map(jar -> jar.getName() + ":" + jar.getVersion())
                .toList();
        variables.put("jars", String.join(", ", jarNames));
        variables.put("jarList", String.join("\n", jarNames));

        List<String> versions = conflict.getConflictingJars().stream()
                .map(JarInfo::getVersion)
                .filter(v -> v != null && !v.equals("unknown"))
                .distinct()
                .toList();
        variables.put("versions", String.join(", ", versions));

        List<String> paths = conflict.getConflictingJars().stream()
                .map(JarInfo::getPath)
                .toList();
        variables.put("paths", String.join("\n", paths));

        return variables;
    }

    /**
     * 按列表顺序评估严重程度规则，首条命中即返回。
     * <p>未命中时，超过三个 JAR 为 MEDIUM，否则为 LOW。
     *
     * @param conflict 尚未赋予严重程度的冲突
     * @return 评估得到的级别
     */
    private SeverityLevel evaluateSeverity(ConflictInfo conflict) {
        String identifier = extractIdentifier(conflict).toLowerCase();
        int jarCount = conflict.getConflictingJars().size();
        String conflictType = conflict.getType().name();

        for (SeverityRule rule : severityRules) {  // 按优先级匹配严重程度规则
            if (rule.matches(identifier, jarCount, conflictType))
                return parseSeverity(rule.getSeverity());
        }

        if (jarCount > 3)  // 默认严重程度逻辑
            return SeverityLevel.MEDIUM;

        return SeverityLevel.LOW;
    }

    /**
     * 忽略大小写解析严重程度；null 或非法值回退为 LOW。
     *
     * @param severity 配置文本
     * @return 解析结果或 LOW
     */
    private SeverityLevel parseSeverity(String severity) {
        if (severity == null)
            return SeverityLevel.LOW;

        try {
            return SeverityLevel.valueOf(severity.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SeverityLevel.LOW;
        }
    }

    /**
     * 将枚举转换为模板使用的中文名称。
     *
     * @param type 冲突类型
     * @return 中文类型名称
     */
    private String getConflictTypeText(ConflictType type) {
        return switch (type) {
            case CLASS_DUPLICATE -> "类重复";
            case VERSION_CONFLICT -> "版本冲突";
            case JAR_DUPLICATE -> "Jar重复";
        };
    }
}
