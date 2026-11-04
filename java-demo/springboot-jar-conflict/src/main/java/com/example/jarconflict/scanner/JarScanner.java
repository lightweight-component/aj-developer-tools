package com.example.jarconflict.scanner;

import com.example.jarconflict.model.JarInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

/**
 * 扫描当前应用 classpath 中的普通 JAR 文件及可识别的开发环境 classes 目录。
 * <p>扫描只读取归档条目和文件系统，不加载或初始化目标类。类名中包含美元符号的
 * 条目会被忽略。版本从 Manifest、Maven 目录标记或文件名推断，可能无法准确识别。
 * <p>排除模式只作用于 classpath 路径，不逐个过滤归档内的类文件。当前按本地文件
 * 打开 URL，未完整支持 Spring Boot 嵌套 JAR；单个路径失败时记录日志并继续。
 */
@Slf4j
@Component
public class JarScanner {
    @Autowired
    private ClassLoaderAdapter classLoaderAdapter;

    @Value("${scanner.exclude-patterns:}")
    private List<String> excludePatterns;

    @Value("${scanner.include-system-jars:false}")
    private boolean includeSystemJars;

    /**
     * 遍历 classpath，扫描普通 JAR 和可识别的 classes 目录。
     * <p>每个 URL 独立处理；失败项不进入结果，空列表不一定表示没有依赖。
     *
     * @return 成功扫描的依赖及开发目录列表
     */
    public List<JarInfo> scanJars() {
        log.info("Starting jar scanning...");
        List<JarInfo> jars = new ArrayList<>();
        List<URL> urls = classLoaderAdapter.getClasspathUrls();

        log.info("Found {} URLs in classpath", urls.size());

        for (URL url : urls) {
            String path = url.getPath();

            if (shouldExclude(path))
                continue;

            if (path.endsWith(".jar")) {
                JarInfo jarInfo = scanJarFile(url);

                if (jarInfo != null)
                    jars.add(jarInfo);
            } else if (path.endsWith("/classes/") || path.contains("target/classes")) {
                JarInfo jarInfo = scanClassesDirectory(url);

                if (jarInfo != null)
                    jars.add(jarInfo);
            }
        }

        log.info("Completed jar scanning, found {} jars", jars.size());
        return jars;
    }

    /**
     * 读取本地 JAR 元数据及非内部类名称；文件在扫描后关闭。
     *
     * @param url 本地 JAR URL
     * @return 扫描结果；文件不存在或读取失败时为 null
     */
    private JarInfo scanJarFile(URL url) {
        String path = url.getPath();
        File jarFile = new File(path);

        if (!jarFile.exists())
            return null;

        try (JarFile jar = new JarFile(jarFile)) {
            JarInfo jarInfo = new JarInfo();
            jarInfo.setPath(path);
            jarInfo.setName(extractJarName(jarFile.getName()));
            jarInfo.setVersion(extractVersion(jar));
            jarInfo.setSize(formatSize(jarFile.length()));
            List<String> classes = new ArrayList<>();

            jar.stream().filter(entry -> entry.getName().endsWith(".class"))
                    .filter(entry -> !entry.getName().contains("$"))
                    .forEach(entry -> {
                        String className = entry.getName()
                                .replace("/", ".")
                                .replace(".class", "");
                        classes.add(className);
                    });

            jarInfo.setClasses(classes);
            log.debug("Scanned jar: {} with {} classes", jarInfo.getName(), classes.size());

            return jarInfo;

        } catch (IOException e) {
            log.warn("Failed to scan jar file: {}", url, e);

            return null;
        }
    }

    /**
     * 扫描开发目录，并以固定名称和 dev 版本表示。
     *
     * @param url 目录 URL
     * @return 目录元数据；不存在或扫描失败时为 null
     */
    private JarInfo scanClassesDirectory(URL url) {
        String path = url.getPath();
        File classesDir = new File(path);

        if (!classesDir.exists())
            return null;

        JarInfo jarInfo = new JarInfo();
        jarInfo.setPath(path);
        jarInfo.setName("classes (development)");
        jarInfo.setVersion("dev");
        jarInfo.setSize(formatSize(calculateDirectorySize(classesDir)));

        List<String> classes = new ArrayList<>();
        scanClassesInDirectory(classesDir, "", classes);
        jarInfo.setClasses(classes);

        log.debug("Scanned classes directory with {} classes", classes.size());

        return jarInfo;
    }

    /**
     * 按目录层级拼接包名，递归收集不含美元符号的类文件名称。
     *
     * @param dir           当前目录
     * @param packagePrefix 当前包名前缀
     * @param classes       接收结果的可变列表
     */
    private void scanClassesInDirectory(File dir, String packagePrefix, List<String> classes) {
        File[] files = dir.listFiles();

        if (files == null)
            return;

        for (File file : files) {
            if (file.isDirectory()) {
                String newPackage = packagePrefix.isEmpty() ?
                        file.getName() : packagePrefix + "." + file.getName();
                scanClassesInDirectory(file, newPackage, classes);
            } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                String className = packagePrefix.isEmpty() ?
                        file.getName().replace(".class", "") :
                        packagePrefix + "." + file.getName().replace(".class", "");
                classes.add(className);
            }
        }
    }

    /**
     * 检查系统路径和配置的排除模式。
     * <p>仅将星号替换成正则片段后整串匹配，并非完整的 Ant 路径匹配；
     * 只检查 classpath 路径，不过滤 JAR 内的类。
     *
     * @param path classpath URL 路径
     * @return 应跳过时为 true
     */
    private boolean shouldExclude(String path) {
        if (!includeSystemJars && isSystemJar(path))
            return true;

        return excludePatterns.stream().anyMatch(pattern -> Pattern.compile(pattern.replace("*", ".*")).matcher(path).matches());
    }

    /**
     * 通过 jre/lib、jdk/lib、java.base 或模块路径等特征判断系统资源。
     *
     * @param path 非空路径
     * @return 符合系统路径特征时为 true
     */
    private boolean isSystemJar(String path) {
        return path.contains("jre/lib") ||
                path.contains("jdk/lib") ||
                path.contains("java.base") ||
                path.startsWith("/modules/");
    }

    /**
     * 去掉 .jar 后缀，并在最后一个连字符后以数字开头时移除该片段。
     *
     * @param fileName JAR 文件名
     * @return 推断的库名
     */
    private String extractJarName(String fileName) {
        if (fileName.endsWith(".jar"))
            fileName = fileName.substring(0, fileName.length() - 4);

        int dashIndex = fileName.lastIndexOf('-');

        if (dashIndex > 0 && dashIndex < fileName.length() - 1) {
            String versionPart = fileName.substring(dashIndex + 1);

            if (versionPart.matches("\\d+.*"))
                return fileName.substring(0, dashIndex);
        }

        return fileName;
    }

    /**
     * 依次尝试 Manifest 的 Implementation-Version、Bundle-Version、Maven 目录标记及文件名。
     * <p>当前不解析 pom.properties；若找到 META-INF/maven 条目，则直接返回 maven。
     *
     * @param jar 已打开的 JAR
     * @return 推断的版本、maven 或 unknown
     */
    private String extractVersion(JarFile jar) {
        Manifest manifest;

        try {
            manifest = jar.getManifest();
        } catch (IOException e) {
            log.warn("Get manifest from Jar failed", e);

            return "unknown";
        }

        if (manifest != null) {
            String version = manifest.getMainAttributes().getValue("Implementation-Version");

            if (version != null)
                return version;

            version = manifest.getMainAttributes().getValue("Bundle-Version");

            if (version != null)
                return version;
        }

        var pomProperties = jar.getEntry("META-INF/maven");
        if (pomProperties != null)
            return "maven";

        return extractVersionFromFileName(jar.getName());

    }

    /**
     * 提取最后一个连字符后的数字开头片段，不完整支持带分类器或限定符的版本。
     *
     * @param fileName 文件名或路径
     * @return 推断的版本或 unknown
     */
    private String extractVersionFromFileName(String fileName) {
        String baseName = Paths.get(fileName).getFileName().toString();

        if (baseName.endsWith(".jar"))
            baseName = baseName.substring(0, baseName.length() - 4);

        int dashIndex = baseName.lastIndexOf('-');

        if (dashIndex > 0 && dashIndex < baseName.length() - 1) {
            String versionPart = baseName.substring(dashIndex + 1);

            if (versionPart.matches("\\d+.*"))
                return versionPart;
        }

        return "unknown";
    }

    /**
     * 按整数截断将字节数格式化为 B、KB 或 MB。
     *
     * @param bytes 字节数
     * @return 供页面展示的大小文本
     */
    private String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";

        if (bytes < 1024 * 1024)
            return (bytes / 1024) + " KB";

        return (bytes / (1024 * 1024)) + " MB";
    }

    /**
     * 递归累加目录文件字节数，包括非 class 资源。
     *
     * @param directory 目标目录
     * @return 累计字节数
     */
    private long calculateDirectorySize(File directory) {
        long size = 0;
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile())
                    size += file.length();
                else if (file.isDirectory())
                    size += calculateDirectorySize(file);
            }
        }

        return size;
    }
}