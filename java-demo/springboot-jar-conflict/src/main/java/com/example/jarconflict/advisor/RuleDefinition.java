package com.example.jarconflict.advisor;

import lombok.Data;

import java.util.List;

/**
 * 一条配置化建议规则，对应 {@code conflict.advisor.rules} 下的一个配置项。
 * <p>规则由正则模式、建议模板和可选严重程度组成。多个模式之间为 OR 关系。
 * 建议生成器使用首条命中的规则，不会合并多条规则的建议。
 */
@Data
public class RuleDefinition {
    /**
     * 匹配冲突标识的正则表达式列表，多个模式为 OR 关系。
     */
    private List<String> patterns;

    /**
     * 建议模板，支持 ConflictAdvisor 提供的变量占位符。
     */
    private String advice;

    /**
     * 可选严重程度文本；命中规则时覆盖冲突已有级别。
     */
    private String severity; // 改为String类型，便于配置绑定

    /**
     * 判断非空输入是否匹配任一正则。
     * <p>输入和正则均先转为小写，再进行整串匹配；子串匹配需显式使用 {@code .*}。
     * 空模式列表返回 false。将正则本身转为小写可能改变正则转义的语义。
     * 非法正则会抛出 PatternSyntaxException，不会自动忽略。
     *
     * @param input 非空冲突标识
     * @return 任一模式匹配时为 true
     */
    public boolean matches(String input) {
        if (patterns == null || patterns.isEmpty())
            return false;

        String lowerInput = input.toLowerCase();

        return patterns.stream().anyMatch(pattern -> lowerInput.matches(pattern.toLowerCase()));
    }
}