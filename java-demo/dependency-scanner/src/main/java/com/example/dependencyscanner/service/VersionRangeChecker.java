package com.example.dependencyscanner.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 Maven DefaultArtifactVersion 的版本比较及简化范围检查工具。
 * <p>支持简单比较、部分 Maven 区间与安全版本的加号后缀。漏洞范围入口先按逗号
 * 拆分，再对片段使用 OR 判断，会拆开双边区间或组合条件，不能替代完整的 Maven 范围解析器。
 * <p>范围判断为 false 仅代表未匹配或缺少输入，不构成依赖无漏洞的证明。
 */
@Slf4j
public class VersionRangeChecker {
    // 版本范围表达式的正则模式
    /**
     * 区间模式，依次捕获左括号、下界、上界和右括号。
     */
    private static final Pattern RANGE_PATTERN = Pattern.compile("^(\\[|\\()?([^,\\[\\]\\(\\)]*)?(?:,([^,\\[\\]\\(\\)]*))?(\\]|\\))?$");

    // 简单比较表达式的正则模式 (如 <=2.14.1, >=1.0.0, <3.0.0, >2.0.0)
    /**
     * 简单比较模式，支持可选运算符及数字开头的版本文本。
     */
    private static final Pattern SIMPLE_PATTERN = Pattern.compile("^(<=|>=|<|>|=)?\\s*([0-9]+(?:\\.[0-9]+)*(?:-[A-Za-z0-9]+)*)$");

    /**
     * 检查指定版本是否在漏洞版本范围内。
     * <p>先按逗号拆分后逐片段判断，任一命中即返回 true；null 输入返回 false。
     * 此拆分会破坏双边 Maven 区间，将组合上下界误作 OR 条件，不能表达完整区间交集。
     *
     * @param version            当前版本；范围工具的公开方法允许 null
     * @param vulnerableVersions 受影响版本表达式
     * @return 任一范围片段匹配时为 true，否则为 false，不代表已确认安全
     */
    public static boolean isVulnerable(String version, String vulnerableVersions) {
        if (version == null || vulnerableVersions == null)
            return false;

        String[] ranges = vulnerableVersions.split("\\s*,\\s*");  // 处理多个范围用逗号分隔的情况

        for (String range : ranges) {
            if (isVersionInRange(version, range.trim()))
                return true;
        }

        return false;

    }

    /**
     * 检查版本是否在指定范围内。
     * <p>依次尝试简单比较、区间正则，最后回退版本字符串精确相等。
     *
     * @param version 当前版本；范围工具的公开方法允许 null
     * @param range   单个版本范围片段
     * @return 当前范围片段匹配时为 true
     */
    private static boolean isVersionInRange(String version, String range) {
        Matcher simpleMatcher = SIMPLE_PATTERN.matcher(range); // 首先尝试简单比较表达式

        if (simpleMatcher.matches())
            return checkSimpleRange(version, simpleMatcher.group(1), simpleMatcher.group(2));

        Matcher rangeMatcher = RANGE_PATTERN.matcher(range); // 尝试Maven风格的范围表达式

        if (rangeMatcher.matches())
            return checkMavenRange(version, rangeMatcher);

        return version.equals(range);  // 如果都不匹配，尝试精确匹配
    }

    /**
     * 检查简单比较表达式 (&lt;=, &gt;=, &lt;, &gt;, =)。
     * <p>使用 DefaultArtifactVersion 比较，运算符为空或等号时判断相等，不识别的运算符返回 false。
     *
     * @param version       当前版本；范围工具的公开方法允许 null
     * @param operator      比较符号，null 在简单比较中等同于等号
     * @param targetVersion 比较目标版本
     * @return 比较条件成立时为 true
     */
    private static boolean checkSimpleRange(String version, String operator, String targetVersion) {
        ArtifactVersion currentVersion = new DefaultArtifactVersion(version);
        ArtifactVersion target = new DefaultArtifactVersion(targetVersion);

        int comparison = currentVersion.compareTo(target);

        if (operator == null || operator.equals("="))
            return comparison == 0;

        switch (operator) {
            case "<=":
                return comparison <= 0;
            case ">=":
                return comparison >= 0;
            case "<":
                return comparison < 0;
            case ">":
                return comparison > 0;
            default:
                return false;
        }
    }

    /**
     * 检查Maven风格的范围表达式。
     * <p>按已匹配正则的捕获组检查上下界：方括号包含边界，圆括号或缺失括号按排除边界处理。
     * 缺失上界时不限制上界；公开入口的逗号拆分会影响区间完整性。
     *
     * @param version 当前版本；范围工具的公开方法允许 null
     * @param matcher 已匹配区间正则且包含上下界捕获组的 Matcher
     * @return 捕获到的上下界条件满足时为 true
     */
    private static boolean checkMavenRange(String version, Matcher matcher) {
        String leftBracket = matcher.group(1);
        String lowerBound = matcher.group(2);
        String upperBound = matcher.group(3);
        String rightBracket = matcher.group(4);

        ArtifactVersion currentVersion = new DefaultArtifactVersion(version);

        if (lowerBound != null && !lowerBound.isEmpty()) { // 检查下界
            ArtifactVersion lower = new DefaultArtifactVersion(lowerBound);
            int lowerComparison = currentVersion.compareTo(lower);

            if ("[".equals(leftBracket)) {
                if (lowerComparison < 0)    // 包含下界
                    return false;
            } else {
                if (lowerComparison <= 0) // 不包含下界
                    return false;
            }
        }

        // 检查上界
        if (upperBound != null && !upperBound.isEmpty()) {
            ArtifactVersion upper = new DefaultArtifactVersion(upperBound);
            int upperComparison = currentVersion.compareTo(upper);

            if ("]".equals(rightBracket))
                return upperComparison <= 0;    // 包含上界
            else
                return upperComparison < 0;// 不包含上界
        }

        return true;
    }

    /**
     * 比较两个版本号。
     * <p>null 小于非 null，两个 null 相等。优先使用 Maven 规则，异常时记录日志并回退字典序。
     *
     * @param version1 第一个版本，可为 null
     * @param version2 第二个版本，可为 null
     * @return 负数表示第一个版本较小，零表示相等，正数表示第一个版本较大
     */
    public static int compareVersions(String version1, String version2) {
        if (version1 == null && version2 == null)
            return 0;
        if (version1 == null)
            return -1;
        if (version2 == null)
            return 1;

        try {
            ArtifactVersion v1 = new DefaultArtifactVersion(version1);
            ArtifactVersion v2 = new DefaultArtifactVersion(version2);

            return v1.compareTo(v2);
        } catch (Exception e) {
            log.warn("版本比较失败: {} vs {}", version1, version2, e);

            return version1.compareTo(version2);
        }
    }

    /**
     * 检查版本是否为安全版本。
     * <p>末尾加号表示不低于基准，显式比较符按符号计算，纯版本默认大于等于。
     * 其他文本回退精确相等，null 输入返回 false。该方法只比较版本，不验证其他漏洞是否存在。
     *
     * @param version     当前版本；范围工具的公开方法允许 null
     * @param safeVersion 修复版本、比较表达式或提示文本
     * @return 满足修复版本表达式时为 true，不代表没有其他漏洞
     */
    public static boolean isSafeVersion(String version, String safeVersion) {
        if (version == null || safeVersion == null)
            return false;

        if (safeVersion.endsWith("+")) { // 处理 "2.15.0+" 格式
            String baseVersion = safeVersion.substring(0, safeVersion.length() - 1);

            return compareVersions(version, baseVersion) >= 0;
        }

        Matcher matcher = SIMPLE_PATTERN.matcher(safeVersion);  // 处理比较表达式

        if (matcher.matches()) {
            String operator = matcher.group(1);
            String targetVersion = matcher.group(2);

            if (operator == null)
                operator = ">="; // 默认为大于等于

            return checkSimpleRange(version, operator, targetVersion);
        }

        return version.equals(safeVersion); // 精确匹配
    }
}
