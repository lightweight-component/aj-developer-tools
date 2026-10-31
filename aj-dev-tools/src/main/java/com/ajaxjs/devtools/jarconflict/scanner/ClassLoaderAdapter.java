package com.ajaxjs.devtools.jarconflict.scanner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 收集当前线程上下文 ClassLoader 及其父级可见的 classpath URL。
 * <p>读取 URLClassLoader 的 URL，以系统属性 {@code java.class.path} 补充，并按 URL 去重。
 * 对名称包含 LaunchedURLClassLoader 的加载器尝试反射提取路径。
 * <p>得到 URL 不代表资源已实际加载，也不保证扫描器支持其协议。
 */
@Component
@Slf4j
public class ClassLoaderAdapter {
    /**
     * 合并加载器层级 URL 与系统 classpath，并去除重复 URL。
     *
     * @return 按发现顺序收集的 URL 列表
     */
    public List<URL> getClasspathUrls() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        log.info("Detecting ClassLoader type: {}", classLoader.getClass().getName());
        List<URL> urls = new ArrayList<>();
        ClassLoader current = classLoader; // 优先尝试获取所有ClassLoader的URLs

        while (current != null) {
            if (current instanceof URLClassLoader urlClassLoader)
                urls.addAll(Arrays.asList(urlClassLoader.getURLs()));

            current = current.getParent();
        }

        if (classLoader.getClass().getName().contains("LaunchedURLClassLoader")) // 如果是Spring Boot LaunchedURLClassLoader，需要特殊处理
            urls.addAll(extractFromLaunchedClassLoader(classLoader));

        urls.addAll(getClasspathFromSystemProperty()); // 添加系统类路径作为补充

        return urls.stream().distinct().collect(ArrayList::new, (list, url) -> { // 去重
            if (!list.contains(url))
                list.add(url);
        }, ArrayList::addAll);
    }

    /**
     * 反射尝试读取 classLoader 和 urls 字段，失败时回退到系统 classpath。
     *
     * @param classLoader 目标加载器
     * @return 提取或回退得到的 URL
     */
    private List<URL> extractFromLaunchedClassLoader(ClassLoader classLoader) {
        List<URL> urls = new ArrayList<>();

        try {
            Field field = classLoader.getClass().getDeclaredField("classLoader");
            field.setAccessible(true);
            var nestedClassLoader = field.get(classLoader);

            if (nestedClassLoader instanceof URLClassLoader urlClassLoader)
                urls.addAll(Arrays.asList(urlClassLoader.getURLs()));

            Field urlsField = classLoader.getClass().getDeclaredField("urls");
            urlsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<URL> urlsList = (List<URL>) urlsField.get(classLoader);

            if (urlsList != null)
                urls.addAll(urlsList);
        } catch (Exception e) {
            log.warn("Failed to extract URLs from LaunchedURLClassLoader, fallback to system property", e);

            return getClasspathFromSystemProperty();
        }

        return urls;
    }

    /**
     * 按平台路径分隔符拆分 java.class.path，并转换为文件 URL。
     *
     * @return 系统属性中的路径 URL 列表
     */
    private List<URL> getClasspathFromSystemProperty() {
        List<URL> urls = new ArrayList<>();
        String classpath = System.getProperty("java.class.path");

        if (classpath != null) {
            String[] paths = classpath.split(File.pathSeparator);

            for (String path : paths) {
                try {
                    File file = new File(path);
                    urls.add(file.toURI().toURL());
                } catch (Exception e) {
                    log.warn("Failed to convert path to URL: {}", path, e);
                }
            }
        }

        return urls;
    }

    /**
     * 通过 BOOT-INF 或 jar!/ 后缀识别路径特征，不验证归档内容。
     *
     * @param jarPath 非空路径
     * @return 具有 Fat JAR 路径特征时为 true
     */
    public boolean isSpringBootFatJar(String jarPath) {
        return jarPath.contains("BOOT-INF") || jarPath.endsWith("jar!/");
    }

    /**
     * 通过系统属性中的 dev 或 target/classes 路径推断开发环境。
     * <p>不读取 Spring Environment，路径检查使用正斜杠，仅作为启发式判断。
     *
     * @return 符合任一特征时为 true
     */
    public boolean isDevelopmentEnvironment() {
        return System.getProperty("spring.profiles.active", "").contains("dev") || System.getProperty("java.class.path", "").contains("target/classes");
    }
}