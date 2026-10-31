package com.ajaxjs.devtools.jarconflict.advisor;

import com.ajaxjs.devtools.jarconflict.model.ConflictInfo;
import com.ajaxjs.devtools.jarconflict.model.ConflictType;
import com.ajaxjs.devtools.jarconflict.model.JarInfo;
import com.ajaxjs.devtools.jarconflict.model.SeverityLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 根据类清单和 JAR 元数据检测类重复、版本冲突及疑似 JAR 重复。
 * <p>按类名、库名及简化签名分别建立索引，为每条结果设置初始严重程度。
 * 同一组依赖可产生多种冲突记录，结果不会跨类型合并。
 * <p>检测结果表示潜在风险，未分析实际类加载优先级、方法签名或二进制兼容性。
 * JAR 重复检测使用启发式签名，不是文件内容一致性的证明。
 */
@Component
@Slf4j
public class ConflictDetector {
    /**
     * 依次执行三种检测，为记录设置初始严重程度。
     *
     * @param jars 非空依赖列表
     * @return 合并的冲突记录，类型内部顺序未定义
     */
    public List<ConflictInfo> detectConflicts(List<JarInfo> jars) {
        log.info("Starting conflict detection for {} jars", jars.size());

        List<ConflictInfo> conflicts = new ArrayList<>();
        conflicts.addAll(detectClassDuplicates(jars));
        conflicts.addAll(detectVersionConflicts(jars));
        conflicts.addAll(detectJarDuplicates(jars));

        log.info("Found {} total conflicts", conflicts.size());

        return conflicts;
    }

    /**
     * 按全限定类名建立反向索引，对出现多次的类生成记录。
     *
     * @param jars 扫描结果
     * @return 类重复记录
     */
    private List<ConflictInfo> detectClassDuplicates(List<JarInfo> jars) {
        log.debug("Detecting class duplicates...");
        Map<String, List<JarInfo>> classToJarsMap = new HashMap<>();

        for (JarInfo jar : jars) {
            if (jar.getClasses() != null) {
                for (String className : jar.getClasses())
                    classToJarsMap.computeIfAbsent(className, k -> new ArrayList<>()).add(jar);
            }
        }

        return classToJarsMap.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1).map(entry -> {
                    ConflictInfo conflict = new ConflictInfo(
                            entry.getKey(),
                            entry.getValue(),
                            ConflictType.CLASS_DUPLICATE
                    );
                    conflict.setSeverity(calculateClassConflictSeverity(entry.getKey(), entry.getValue()));

                    return conflict;
                }).collect(Collectors.toList());
    }

    /**
     * 按库名分组，同组具有多个已知版本时生成冲突。
     *
     * @param jars 扫描结果
     * @return 版本冲突记录
     */
    private List<ConflictInfo> detectVersionConflicts(List<JarInfo> jars) {
        log.debug("Detecting version conflicts...");
        Map<String, List<JarInfo>> nameToJarsMap = jars.stream()
                .collect(Collectors.groupingBy(JarInfo::getName));

        return nameToJarsMap.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .filter(entry -> hasVersionConflict(entry.getValue()))
                .map(entry -> {
                    ConflictInfo conflict = new ConflictInfo(
                            entry.getKey(),
                            entry.getValue(),
                            ConflictType.VERSION_CONFLICT
                    );
                    conflict.setSeverity(calculateVersionConflictSeverity(entry.getKey(), entry.getValue()));
                    return conflict;
                })
                .collect(Collectors.toList());
    }

    /**
     * 按简化签名分组以查找疑似重复 JAR，不比较文件内容或哈希。
     *
     * @param jars 扫描结果
     * @return 疑似 JAR 重复记录
     */
    private List<ConflictInfo> detectJarDuplicates(List<JarInfo> jars) {
        log.debug("Detecting jar duplicates...");
        Map<String, List<JarInfo>> pathSignatureMap = new HashMap<>();

        for (JarInfo jar : jars) {
            String signature = generateJarSignature(jar);
            pathSignatureMap.computeIfAbsent(signature, k -> new ArrayList<>()).add(jar);
        }

        return pathSignatureMap.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> {
                    String className = "Duplicate JAR: " + entry.getValue().get(0).getName();
                    ConflictInfo conflict = new ConflictInfo(
                            className,
                            entry.getValue(),
                            ConflictType.JAR_DUPLICATE
                    );
                    conflict.setSeverity(SeverityLevel.MEDIUM);

                    return conflict;
                })
                .collect(Collectors.toList());
    }

    /**
     * 排除 null 和 unknown 后，判断不同版本字符串是否超过一个。
     *
     * @param jars 同名库的依赖
     * @return 存在多个已知版本时为 true
     */
    private boolean hasVersionConflict(List<JarInfo> jars) {
        Set<String> versions = jars.stream()
                .map(JarInfo::getVersion)
                .filter(v -> v != null && !v.equals("unknown"))
                .collect(Collectors.toSet());

        return versions.size() > 1;
    }

    /**
     * 按关键类、系统或框架类以及依赖数量计算初始级别。
     * <p>关键类为 CRITICAL，系统或框架类为 HIGH，其余超过三个 JAR 为 MEDIUM，否则 LOW。
     *
     * @param className 全限定类名
     * @param jars      包含此类的依赖
     * @return 初始严重程度
     */
    private SeverityLevel calculateClassConflictSeverity(String className, List<JarInfo> jars) {
        if (isCriticalClass(className))
            return SeverityLevel.CRITICAL;

        if (isSystemClass(className))
            return SeverityLevel.HIGH;

        if (isFrameworkClass(className))
            return SeverityLevel.HIGH;

        if (jars.size() > 3)
            return SeverityLevel.MEDIUM;

        return SeverityLevel.LOW;
    }

    /**
     * 按关键库、框架库和主版本差异计算初始级别。
     * <p>关键库为 CRITICAL，框架库或跨主版本为 HIGH，其余为 MEDIUM。
     *
     * @param jarName 库名
     * @param jars    同名库的依赖
     * @return 初始严重程度
     */
    private SeverityLevel calculateVersionConflictSeverity(String jarName, List<JarInfo> jars) {
        if (isCriticalLibrary(jarName))
            return SeverityLevel.CRITICAL;

        if (isFrameworkLibrary(jarName))
            return SeverityLevel.HIGH;

        Set<String> majorVersions = jars.stream()
                .map(jar -> extractMajorVersion(jar.getVersion()))
                .collect(Collectors.toSet());

        if (majorVersions.size() > 1)
            return SeverityLevel.HIGH;

        return SeverityLevel.MEDIUM;
    }

    /**
     * 按包前缀或 log、Driver 等子串启发式识别关键类。
     *
     * @param className 非空类名
     * @return 符合关键类特征时为 true
     */
    private boolean isCriticalClass(String className) {
        return className.startsWith("java.") ||
                className.startsWith("javax.") ||
                className.startsWith("org.slf4j.") ||
                className.startsWith("org.apache.logging.") ||
                className.contains("log") ||
                className.contains("Driver");
    }

    /**
     * 识别 Spring、Hibernate 和 Jackson 的类名前缀。
     *
     * @param className 非空类名
     * @return 符合预设前缀时为 true
     */
    private boolean isSystemClass(String className) {
        return className.startsWith("org.springframework.") ||
                className.startsWith("org.hibernate.") ||
                className.startsWith("com.fasterxml.jackson.");
    }

    /**
     * 识别 Apache、数据库驱动和 Redis 客户端的类名前缀。
     *
     * @param className 非空类名
     * @return 符合预设前缀时为 true
     */
    private boolean isFrameworkClass(String className) {
        return className.startsWith("org.apache.") ||
                className.startsWith("com.mysql.") ||
                className.startsWith("org.postgresql.") ||
                className.startsWith("redis.clients.");
    }

    /**
     * 按日志、数据库和 driver 相关名称子串识别关键库。
     *
     * @param jarName 非空库名
     * @return 符合关键库特征时为 true
     */
    private boolean isCriticalLibrary(String jarName) {
        return jarName.contains("slf4j") ||
                jarName.contains("logback") ||
                jarName.contains("log4j") ||
                jarName.contains("mysql") ||
                jarName.contains("postgresql") ||
                jarName.contains("driver");
    }

    /**
     * 按 Spring、Hibernate、Jackson 和 Apache 名称特征识别框架库。
     *
     * @param jarName 非空库名
     * @return 符合框架库特征时为 true
     */
    private boolean isFrameworkLibrary(String jarName) {
        return jarName.startsWith("spring") ||
                jarName.contains("hibernate") ||
                jarName.contains("jackson") ||
                jarName.contains("apache");
    }

    /**
     * 取版本字符串第一个点之前的片段，不验证语义化版本格式。
     *
     * @param version 版本字符串，可为空
     * @return 主版本片段或 unknown
     */
    private String extractMajorVersion(String version) {
        if (version == null || version.equals("unknown"))
            return "unknown";

        String[] parts = version.split("\\.");

        return parts.length > 0 ? parts[0] : version;
    }

    /**
     * 构造启发式签名：类清单非空时使用“名称:类数量”，否则使用“名称:版本”。
     * <p>同名且类数量相同的不同归档可能具有相同签名，不能据此证明内容相同。
     *
     * @param jar 目标依赖
     * @return 分组使用的签名
     */
    private String generateJarSignature(JarInfo jar) {
        if (jar.getClasses() == null || jar.getClasses().isEmpty())
            return jar.getName() + ":" + jar.getVersion();

        return jar.getName() + ":" + jar.getClasses().size();
    }
}