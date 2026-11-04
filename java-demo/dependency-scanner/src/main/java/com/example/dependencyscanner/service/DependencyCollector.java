package com.example.dependencyscanner.service;

import com.example.dependencyscanner.model.DependencyInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通过加载器、文件系统及 Maven 命令收集候选依赖坐标。
 * <p>依次检查上下文 URLClassLoader、系统 classpath、预设代表类的代码来源、
 * 项目 target、Maven 输出及默认本地仓库的常用目录。BOOT-INF/lib 方法目前仅为占位。
 * <p>结果不是严格的运行时依赖快照：仓库可包含未使用组件与旧版本，文本解析未统一
 * 过滤作用域，Manifest 和文件名推断也不保证为真实 Maven 坐标。
 * <p>收集可能启动 Maven 子进程、写入 target 及下载依赖，子进程等待未设置超时。
 * 文件来源按文件名去重，文本来源按坐标去重，不能保证跨来源唯一。
 */
@Service
@Slf4j
public class DependencyCollector {
    /**
     * Maven坐标解析正则表达式
     * 从文件名末尾提取组件名与数字开头版本的简化模式，不解析完整坐标。
     */
    private static final Pattern MAVEN_JAR_PATTERN = Pattern.compile(
            "([^/\\\\]+)-([0-9]+(?:\\.[0-9]+)*(?:-[A-Za-z0-9]+)*)\\.jar$"
    );

    /**
     * 收集当前应用的所有依赖信息。
     * <p>每次新建结果列表和去重集合，按顺序执行全部收集策略。多数子策略自行记录异常；
     * 未被内部处理的异常在此捕获并终止剩余策略，仍返回之前累计的结果。
     *
     * @return 候选依赖的可变列表，可能为空，也可能包含跨来源重复项
     */
    public List<DependencyInfo> collect() {
        log.info("开始收集依赖信息...");

        List<DependencyInfo> dependencies = new ArrayList<>();
        Set<String> processedJars = new HashSet<>();

        // 方法1: 尝试从URLClassLoader获取
        collectFromURLClassLoader(dependencies, processedJars);
        log.info("URLClassLoader方法收集到 {} 个依赖", dependencies.size());

        // 方法2: 从系统类路径获取（适用于IDEA等IDE环境）
        collectFromSystemClassPath(dependencies, processedJars);
        log.info("系统类路径方法总共收集到 {} 个依赖", dependencies.size());

        // 方法3: 从已加载的类中推断依赖（IDEA环境最可靠的方法）
        collectFromLoadedClasses(dependencies, processedJars);
        log.info("已加载类方法总共收集到 {} 个依赖", dependencies.size());

        // 方法4: 从Maven本地仓库路径获取（IDEA环境常用）
        collectFromMavenRepository(dependencies, processedJars);
        log.info("Maven仓库方法总共收集到 {} 个依赖", dependencies.size());

        // 方法5: 尝试从Spring Boot的BOOT-INF/lib目录获取依赖
        collectFromBootInfLib(dependencies, processedJars);
        log.info("BOOT-INF/lib方法总共收集到 {} 个依赖", dependencies.size());
        log.info("依赖收集完成，共找到 {} 个依赖", dependencies.size());

        return dependencies;
    }

    /**
     * 从URLClassLoader获取依赖。
     * <p>仅检查当前线程上下文加载器，不遍历父加载器，只处理 URL 文件部分以 .jar 结尾的项。
     * 失败记录日志后返回，不向调用者传播。
     *
     * @param dependencies  接收候选依赖的非空可变列表，本方法向其中追加结果
     * @param processedJars 本次收集共享的可变去重集合，包含文件名或 Maven 坐标
     */
    private void collectFromURLClassLoader(List<DependencyInfo> dependencies, Set<String> processedJars) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        if (classLoader instanceof URLClassLoader) {
            log.info("使用URLClassLoader收集依赖");
            URLClassLoader urlClassLoader = (URLClassLoader) classLoader;
            URL[] urls = urlClassLoader.getURLs();

            for (URL url : urls) {
                if (url.getFile().endsWith(".jar"))
                    processDependency(url, dependencies, processedJars);
            }
        }
    }

    /**
     * 从系统类路径获取依赖（适用于IDEA等IDE环境）。
     * <p>按平台路径分隔符拆分 java.class.path，仅处理存在的普通 JAR，不将目录作为依赖。
     *
     * @param dependencies  接收候选依赖的非空可变列表，本方法向其中追加结果
     * @param processedJars 本次收集共享的可变去重集合，包含文件名或 Maven 坐标
     */
    private void collectFromSystemClassPath(List<DependencyInfo> dependencies, Set<String> processedJars) {
        log.info("使用系统类路径收集依赖");
        String classPath = System.getProperty("java.class.path");

        if (classPath != null) {
            String[] paths = classPath.split(File.pathSeparator);
            log.info("类路径包含 {} 个条目", paths.length);

            for (String path : paths) {
                if (path.endsWith(".jar")) {
                    File jarFile = new File(path);

                    if (jarFile.exists()) {
                        try {
                            processDependency(jarFile.toURI().toURL(), dependencies, processedJars);
                        } catch (MalformedURLException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }

    }

    /**
     * 检查一些常见的Spring Boot类，如果存在则推断相关依赖
     */
    final static String[] commonClasses = {
            "org.springframework.boot.SpringApplication",
            "org.springframework.web.servlet.DispatcherServlet",
            "org.springframework.jdbc.core.JdbcTemplate",
            "com.fasterxml.jackson.databind.ObjectMapper",
            "org.apache.tomcat.embed.core.StandardEngine",
            "org.h2.Driver",
            "org.slf4j.LoggerFactory"
    };

    /**
     * 从已加载的类中推断依赖（IDEA环境最可靠的方法）。
     * <p>通过 Class.forName 尝试解析预设代表类，禁止类初始化，并取 CodeSource 中的归档位置。
     * 并未枚举 JVM 全部已加载类。代表类缺失或不指向普通 JAR 时跳过。
     *
     * @param dependencies  接收候选依赖的非空可变列表，本方法向其中追加结果
     * @param processedJars 本次收集共享的可变去重集合，包含文件名或 Maven 坐标
     */
    private void collectFromLoadedClasses(List<DependencyInfo> dependencies, Set<String> processedJars) {
        log.info("从已加载的类中推断依赖");
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Class<?> classLoaderClass = classLoader.getClass(); // 尝试通过反射获取已加载的类
        Set<String> foundPackages = new HashSet<>();

        for (String className : commonClasses) {
            try {
                Class<?> clazz = Class.forName(className, false, classLoader);
                CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();  // 获取类的代码源位置

                if (codeSource != null && codeSource.getLocation() != null) {
                    String location = codeSource.getLocation().getPath();

                    if (location.endsWith(".jar")) {
                        File jarFile = new File(location);

                        if (jarFile.exists() && !processedJars.contains(jarFile.getAbsolutePath())) {
                            processDependency(jarFile.toURI().toURL(), dependencies, processedJars);
                            // 记录找到的包
                            String packageName = className.substring(0, className.lastIndexOf('.'));
                            foundPackages.add(packageName);
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                // 类不存在，跳过
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        log.info("通过已加载类推断找到 {} 个包: {}", foundPackages.size(), foundPackages);
    }

    /**
     * 从Maven本地仓库收集依赖（IDEA环境常用）。
     * <p>依次扫描工作目录的 target、执行 Maven 收集策略，再检查用户目录的 .m2/repository。
     * 不读取 settings.xml 中自定义的仓库位置，仓库内容不保证是本项目依赖。
     *
     * @param dependencies  接收候选依赖的非空可变列表，本方法向其中追加结果
     * @param processedJars 本次收集共享的可变去重集合，包含文件名或 Maven 坐标
     */
    private void collectFromMavenRepository(List<DependencyInfo> dependencies, Set<String> processedJars) {
        // 方法1: 扫描target目录
        String projectPath = System.getProperty("user.dir");
        File targetDir = new File(projectPath, "target");

        if (targetDir.exists()) {
            log.info("扫描项目target目录: {}", targetDir.getAbsolutePath());
            scanTargetDirectory(targetDir, dependencies, processedJars);
        }

        // 方法2: 尝试通过Runtime执行Maven命令获取依赖
        log.info("尝试通过Maven命令获取依赖列表");
        collectDependenciesViaMaven(dependencies, processedJars);

        // 方法3: 扫描Maven本地仓库中的常用依赖
        String userHome = System.getProperty("user.home");
        String mavenRepo = userHome + File.separator + ".m2" + File.separator + "repository";
        File repoDir = new File(mavenRepo);

        if (repoDir.exists() && repoDir.isDirectory()) {
            log.info("扫描Maven本地仓库: {}", mavenRepo);
            scanCommonDependencies(repoDir, dependencies, processedJars);
        }
    }

    /**
     * 通过Maven命令获取依赖。
     * <p>工作目录存在 pom.xml 时，依次尝试 tree、list、copy-dependencies。
     * 复制阶段仅请求 runtime 作用域并写入 target/dependency；文本解析未统一过滤作用域。
     * 等待子进程没有超时，异常记录日志后结束本策略。
     *
     * @param dependencies  接收候选依赖的非空可变列表，本方法向其中追加结果
     * @param processedJars 本次收集共享的可变去重集合，包含文件名或 Maven 坐标
     */
    private void collectDependenciesViaMaven(List<DependencyInfo> dependencies, Set<String> processedJars) {
        String projectPath = System.getProperty("user.dir");
        File pomFile = new File(projectPath, "pom.xml");

        if (pomFile.exists()) {
            // 首先尝试解析Maven dependency:tree输出
            parseMavenDependencyTree(dependencies, processedJars, projectPath);

            // 然后尝试Maven dependency:list命令获取更准确的依赖列表
            parseMavenDependencyList(dependencies, processedJars, projectPath);

            // 最后执行Maven dependency:copy-dependencies命令作为备用方案
            if (isMavenAvailable()) {
                ProcessBuilder pb = new ProcessBuilder(
                        getMavenCommand(),
                        "dependency:copy-dependencies",
                        "-DoutputDirectory=target/dependency",
                        "-DincludeScope=runtime"
                );
                pb.directory(new File(projectPath));
                pb.redirectErrorStream(true);
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD); // 使用JDK9的方法忽略输出流，避免阻塞

                Process process;
                int exitCode;

                try {
                    process = pb.start();
                    exitCode = process.waitFor();
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }

                if (exitCode == 0) {
                    log.info("Maven依赖复制成功");
                    File dependencyDir = new File(projectPath, "target/dependency");

                    if (dependencyDir.exists())
                        scanJarDirectory(dependencyDir, dependencies, processedJars);
                } else
                    log.warn("Maven依赖复制失败，退出码: {}", exitCode);
            } else
                log.warn("Maven命令不可用，跳过dependency:copy-dependencies");
        }

    }

    /**
     * 解析Maven dependency:tree输出。
     * <p>同步执行 dependency:tree，解析带树形前缀的 JAR 坐标和三段简化格式，按坐标去重。
     * 来源标记为 maven-tree 或 maven-tree-simple。按冒号位置拆分，不完整支持 classifier。
     * 读取到的 scope 仅用于日志，不用于排除依赖。
     *
     * @param dependencies  接收候选依赖的非空可变列表，本方法向其中追加结果
     * @param processedJars 本次收集共享的可变去重集合，包含文件名或 Maven 坐标
     * @param projectPath   项目工作目录，包含 pom.xml，用于启动 Maven 及定位输出文件
     */
    private void parseMavenDependencyTree(List<DependencyInfo> dependencies, Set<String> processedJars, String projectPath) {
        try {
            // 检查Maven是否可用
            if (!isMavenAvailable()) {
                log.warn("Maven命令不可用，跳过Maven dependency:tree解析");
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(getMavenCommand(), "dependency:tree", "-DoutputType=text");
            pb.directory(new File(projectPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));

            String line;
            // 改进的正则表达式，匹配Maven dependency:tree的输出格式
            // 支持更多的格式变体
            Pattern dependencyPattern1 = Pattern.compile(".*?([\\w\\.\\-]+):([\\w\\.\\-]+):(jar|war|pom):([\\w\\.\\-]+):(compile|runtime|test|provided|system).*");
            Pattern dependencyPattern2 = Pattern.compile(".*?([\\w\\.\\-]+):([\\w\\.\\-]+):(jar|war|pom):([\\w\\.\\-]+).*");

            int parsedCount = 0;
            while ((line = reader.readLine()) != null) {
                log.debug("Maven tree输出行: {}", line);

                // 检查是否包含依赖信息的行
                if (line.contains("[INFO]") && line.contains(":") &&
                        (line.contains("+- ") || line.contains("\\- ") || line.contains("|- ") || line.contains("|  "))) {

                    // 清理行内容，移除前缀
                    String cleanLine = line.replaceAll("\\[INFO\\]\\s*", "")
                            .replaceAll("^[\\s\\|+\\\\-]*", "")
                            .trim();

                    if (cleanLine.contains(":")) {
                        String[] parts = cleanLine.split(":");
                        if (parts.length >= 4) {
                            String groupId = parts[0].trim();
                            String artifactId = parts[1].trim();
                            String packaging = parts[2].trim();
                            String version = parts[3].trim();
                            String scope = parts.length > 4 ? parts[4].trim() : "compile";

                            // 处理jar包依赖
                            if ("jar".equals(packaging)) {
                                String jarKey = groupId + ":" + artifactId + ":" + version;
                                if (!processedJars.contains(jarKey)) {
                                    processedJars.add(jarKey);
                                    DependencyInfo depInfo = new DependencyInfo(groupId, artifactId, version, "maven-tree");
                                    dependencies.add(depInfo);
                                    parsedCount++;
                                    log.debug("从Maven tree解析到依赖: {}:{}:{} (scope: {})", groupId, artifactId, version, scope);
                                }
                            }
                        } else if (parts.length == 3) {
                            // 处理简化格式 groupId:artifactId:version
                            String groupId = parts[0].trim();
                            String artifactId = parts[1].trim();
                            String version = parts[2].trim();

                            String jarKey = groupId + ":" + artifactId + ":" + version;
                            if (!processedJars.contains(jarKey)) {
                                processedJars.add(jarKey);
                                DependencyInfo depInfo = new DependencyInfo(groupId, artifactId, version, "maven-tree-simple");
                                dependencies.add(depInfo);
                                parsedCount++;
                                log.debug("从Maven tree简化格式解析到依赖: {}:{}:{}", groupId, artifactId, version);
                            }
                        }
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0)
                log.info("Maven dependency:tree解析完成，解析到{}个依赖", parsedCount);
            else
                log.warn("Maven dependency:tree执行失败，退出码: {}", exitCode);
        } catch (Exception e) {
            log.warn("解析Maven dependency:tree失败: {}", e.getMessage());
        }
    }

    /**
     * 通过Maven dependency:list命令解析依赖。
     * <p>执行 dependency:list 并读取 target/dependency-list.txt，跳过空行及注释。
     * 按坐标去重，使用 maven-list 来源标记；不按作用域筛选，不完整支持带 classifier 的格式。
     *
     * @param dependencies  接收候选依赖的非空可变列表，本方法向其中追加结果
     * @param processedJars 本次收集共享的可变去重集合，包含文件名或 Maven 坐标
     * @param projectPath   项目工作目录，包含 pom.xml，用于启动 Maven 及定位输出文件
     */
    private void parseMavenDependencyList(List<DependencyInfo> dependencies, Set<String> processedJars, String projectPath) {
        try {
            if (!isMavenAvailable()) {
                log.debug("Maven命令不可用，跳过dependency:list解析");
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(getMavenCommand(), "dependency:list", "-DoutputFile=target/dependency-list.txt", "-DappendOutput=false");
            pb.directory(new File(projectPath));
            pb.redirectErrorStream(true);
            // 使用JDK9的方法忽略输出流，避免阻塞
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.warn("Maven dependency:list命令执行失败，退出码: {}", exitCode);
                return;
            }

            // 读取输出文件
            File outputFile = new File(projectPath, "target/dependency-list.txt");
            if (!outputFile.exists()) {
                log.warn("dependency:list输出文件不存在: {}", outputFile.getAbsolutePath());
                return;
            }

            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(outputFile))) {
                String line;
                int parsedCount = 0;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    // 跳过空行和注释行
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("//"))
                        continue;

                    // 解析依赖格式: groupId:artifactId:packaging:version:scope
                    String[] parts = line.split(":");
                    if (parts.length >= 4) {
                        String groupId = parts[0].trim();
                        String artifactId = parts[1].trim();
                        String packaging = parts[2].trim();
                        String version = parts[3].trim();
                        String scope = parts.length > 4 ? parts[4].trim() : "compile";

                        // 只处理jar包依赖
                        if ("jar".equals(packaging)) {
                            String jarKey = groupId + ":" + artifactId + ":" + version;
                            if (!processedJars.contains(jarKey)) {
                                processedJars.add(jarKey);
                                DependencyInfo depInfo = new DependencyInfo(groupId, artifactId, version, "maven-list");
                                dependencies.add(depInfo);
                                parsedCount++;
                                log.debug("从Maven dependency:list解析到依赖: {}:{}:{} (scope: {})", groupId, artifactId, version, scope);
                            }
                        }
                    }
                }

                log.info("从Maven dependency:list解析到 {} 个依赖", parsedCount);
            }

        } catch (Exception e) {
            log.warn("解析Maven dependency:list失败", e);
        }
    }

    /**
     * 检查Maven命令是否可用。
     * <p>执行平台对应命令的 --version，丢弃输出并同步等待退出码。未设置等待超时，
     * 启动或等待异常时返回 false；中断异常也被捕获，不恢复线程中断标记。
     *
     * @return Maven 进程正常退出且退出码为零时为 true，否则为 false
     */
    private boolean isMavenAvailable() {
        try {
            // 在Windows系统中使用mvn.cmd，在其他系统中使用mvn
            String mvnCommand = System.getProperty("os.name").toLowerCase().contains("windows") ? "mvn.cmd" : "mvn";
            ProcessBuilder pb = new ProcessBuilder(mvnCommand, "--version");
            pb.redirectErrorStream(true);
            // 使用JDK9的方法忽略输出流，避免阻塞
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.debug("Maven命令检查失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取适合当前操作系统的Maven命令。
     * <p>依据 os.name 选择命令，Windows 使用 mvn.cmd，其他系统使用 mvn。
     *
     * @return 当前系统适用的 mvn.cmd 或 mvn 命令名
     */
    private String getMavenCommand() {
        return System.getProperty("os.name").toLowerCase().contains("windows") ? "mvn.cmd" : "mvn";
    }

    /**
     * 扫描Maven仓库中的常用依赖。
     * <p>从默认仓库的预设 Spring、Jackson、Tomcat 等目录开始，以三层剩余深度递归。
     * 不依据项目依赖树筛选归档或版本，可能包含项目未使用的依赖。
     *
     * @param repoDir       默认 Maven 本地仓库根目录
     * @param dependencies  接收候选依赖的非空可变列表，本方法向其中追加结果
     * @param processedJars 本次收集共享的可变去重集合，包含文件名或 Maven 坐标
     */
    private void scanCommonDependencies(File repoDir, List<DependencyInfo> dependencies, Set<String> processedJars) {
        try {
            // 扫描一些常用的Spring Boot依赖
            String[] commonPaths = {
                    "org/springframework",
                    "org/springframework/boot",
                    "org/springframework/security",
                    "com/fasterxml/jackson",
                    "org/apache/tomcat",
                    "org/hibernate",
                    "mysql",
                    "com/h2database"
            };

            for (String path : commonPaths) {
                File depDir = new File(repoDir, path);
                if (depDir.exists()) {
                    scanRepositoryDirectory(depDir, dependencies, processedJars, 3); // 限制扫描深度
                }
            }
        } catch (Exception e) {
            log.warn("扫描常用依赖失败", e);
        }
    }

    /**
     * 递归扫描仓库目录。
     * <p>剩余深度小于等于零时返回，每进入子目录深度减一。
     * 跳过名称包含 -sources 或 -javadoc 的 JAR；失败记录日志并保留已有结果。
     *
     * @param dir           当前递归处理目录
     * @param dependencies  接收候选依赖的非空可变列表，本方法向其中追加结果
     * @param processedJars 本次收集共享的可变去重集合，包含文件名或 Maven 坐标
     * @param maxDepth      剩余目录层数，小于等于零时返回
     */
    private void scanRepositoryDirectory(File dir, List<DependencyInfo> dependencies, Set<String> processedJars, int maxDepth) {
        if (maxDepth <= 0)
            return;

        try {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory())
                        scanRepositoryDirectory(file, dependencies, processedJars, maxDepth - 1);
                    else if (file.getName().endsWith(".jar") && !file.getName().contains("-sources") && !file.getName().contains("-javadoc"))
                        processDependency(file.toURI().toURL(), dependencies, processedJars);
                }
            }
        } catch (Exception e) {
            log.warn("扫描仓库目录失败: {}", dir.getPath(), e);
        }
    }

    /**
     * 扫描target目录获取依赖信息。
     * <p>仅扫描 target 直属 dependency、lib 目录及根目录中的 JAR，
     * 不递归所有构建目录，也不展开可执行归档中的嵌套依赖。
     *
     * @param targetDir     项目 target 目录
     * @param dependencies  接收候选依赖的非空可变列表，本方法向其中追加结果
     * @param processedJars 本次收集共享的可变去重集合，包含文件名或 Maven 坐标
     */
    private void scanTargetDirectory(File targetDir, List<DependencyInfo> dependencies, Set<String> processedJars) {
        try {
            // 查找Maven依赖目录
            File[] subdirs = targetDir.listFiles(File::isDirectory);
            if (subdirs != null) {
                for (File subdir : subdirs) {
                    if (subdir.getName().equals("dependency") || subdir.getName().equals("lib"))
                        scanJarDirectory(subdir, dependencies, processedJars);
                }
            }

            // 也扫描target根目录下的jar文件
            File[] jarFiles = targetDir.listFiles((dir, name) -> name.endsWith(".jar"));
            if (jarFiles != null) {
                for (File jarFile : jarFiles)
                    processDependency(jarFile.toURI().toURL(), dependencies, processedJars);
            }
        } catch (Exception e) {
            log.warn("扫描target目录失败", e);
        }
    }

    /**
     * 扫描指定目录下的jar文件。
     * <p>只处理指定目录直属的 .jar 文件，不递归子目录。扫描异常记录日志。
     *
     * @param directory     只扫描直属 JAR 的目标目录
     * @param dependencies  接收候选依赖的非空可变列表，本方法向其中追加结果
     * @param processedJars 本次收集共享的可变去重集合，包含文件名或 Maven 坐标
     */
    private void scanJarDirectory(File directory, List<DependencyInfo> dependencies, Set<String> processedJars) {
        try {
            File[] jarFiles = directory.listFiles((dir, name) -> name.endsWith(".jar"));
            if (jarFiles != null) {
                for (File jarFile : jarFiles)
                    processDependency(jarFile.toURI().toURL(), dependencies, processedJars);
            }
        } catch (Exception e) {
            log.warn("扫描jar目录失败: {}", directory.getPath(), e);
        }
    }

    /**
     * 处理单个依赖。
     * <p>以 JAR 文件名去重，并在解析前将名称放入去重集合。
     * 同名不同路径会被跳过，首次解析失败也不会重试同名项；键与 Maven 文本策略不同。
     *
     * @param jarUrl        待解析归档 URL，当前按文件路径解释
     * @param dependencies  接收候选依赖的非空可变列表，本方法向其中追加结果
     * @param processedJars 本次收集共享的可变去重集合，包含文件名或 Maven 坐标
     */
    private void processDependency(URL jarUrl, List<DependencyInfo> dependencies, Set<String> processedJars) {
        try {
            String jarPath = jarUrl.getFile();
            String jarName = new File(jarPath).getName();

            // 避免重复处理
            if (processedJars.contains(jarName))
                return;
            processedJars.add(jarName);

            DependencyInfo dependency = parseDependencyFromJar(jarPath, jarName);
            if (dependency != null) {
                dependencies.add(dependency);
                log.debug("发现依赖: {}:{}:{}", dependency.getGroupId(),
                        dependency.getArtifactId(), dependency.getVersion());
            }

        } catch (Exception e) {
            log.warn("处理jar包时发生错误: {}", jarUrl, e);
        }
    }

    /**
     * 从jar包解析依赖信息。
     * <p>优先读取 Manifest，缺少有效 artifactId 和 version 时回退文件名。
     * 未处理的解析异常记录日志后返回 null；不读取 pom.properties。
     *
     * @param jarPath 归档路径或发现来源标记
     * @param jarName 归档文件名
     * @return 推断的依赖，异常时可能为 null
     */
    private DependencyInfo parseDependencyFromJar(String jarPath, String jarName) {
        try {
            // 首先尝试从MANIFEST.MF获取信息
            DependencyInfo dependency = parseFromManifest(jarPath);
            if (dependency != null)
                return dependency;

            // 如果MANIFEST.MF没有信息，尝试从文件名解析
            return parseFromFileName(jarPath, jarName);

        } catch (Exception e) {
            log.warn("解析jar包信息失败: {}", jarPath, e);
            return null;
        }
    }

    /**
     * 从MANIFEST.MF文件解析依赖信息。
     * <p>读取主属性推断坐标，JarFile 自动关闭。只有 artifactId 和 version 均非空时创建记录，
     * 未知 groupId 再按名称推断。Manifest 展示名称不保证等于 Maven 坐标；读取失败或字段缺失返回 null。
     *
     * @param jarPath 归档路径或发现来源标记
     * @return 推断的依赖，缺少字段或读取失败时为 null
     */
    private DependencyInfo parseFromManifest(String jarPath) {
        try (JarFile jarFile = new JarFile(jarPath)) {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null)
                return null;

            Attributes mainAttributes = manifest.getMainAttributes();

            // 尝试从多个属性获取groupId、artifactId和version
            String groupId = extractGroupId(mainAttributes);
            String artifactId = extractArtifactId(mainAttributes);
            String version = extractVersion(mainAttributes);

            if (artifactId != null && version != null) {
                // 如果groupId仍然未知，尝试从artifactId推断
                if ("unknown".equals(groupId))
                    groupId = inferGroupIdFromArtifactId(artifactId);

                return new DependencyInfo(groupId, artifactId, version, jarPath);
            }

        } catch (IOException e) {
            log.debug("读取MANIFEST.MF失败: {}", jarPath);
        }

        return null;
    }

    /**
     * 从MANIFEST.MF属性中提取groupId。
     * <p>顺序检查厂商与构建属性，优先接受类似点分包名的值，再识别常见厂商。
     * Created-By、Built-By 也参与猜测，结果不保证是真实坐标；无法识别返回 unknown。
     *
     * @param attributes 非空 Manifest 主属性
     * @return 推断的组名，未识别时为 unknown
     */
    private String extractGroupId(Attributes attributes) {
        // 尝试多个可能的groupId属性
        String[] groupIdKeys = {
                "Implementation-Vendor-Id",
                "Bundle-Vendor",
                "Implementation-Vendor",
                "Specification-Vendor",
                "Created-By",
                "Built-By"
        };

        for (String key : groupIdKeys) {
            String value = attributes.getValue(key);
            if (value != null && !value.trim().isEmpty()) {
                // 清理和标准化groupId
                value = value.trim();

                // 如果包含常见的groupId模式，直接使用
                if (value.matches("^[a-zA-Z][a-zA-Z0-9_.-]*\\.[a-zA-Z][a-zA-Z0-9_.-]*$"))
                    return value;

                // 尝试从vendor信息推断groupId
                if (value.toLowerCase().contains("apache"))
                    return "org.apache";
                else if (value.toLowerCase().contains("springframework") || value.toLowerCase().contains("spring"))
                    return "org.springframework";
                else if (value.toLowerCase().contains("eclipse"))
                    return "org.eclipse";
                else if (value.toLowerCase().contains("fasterxml"))
                    return "com.fasterxml.jackson.core";
            }
        }

        return "unknown";
    }

    /**
     * 从MANIFEST.MF属性中提取artifactId。
     * <p>顺序检查实现标题、Bundle 名称、规范标题和模块名，去掉 Bundle-SymbolicName 的分号后缀。
     * 部分点分名称只取最后一段；结果不保证为真实 artifactId，没有有效值返回 null。
     *
     * @param attributes 非空 Manifest 主属性
     * @return 推断的组件名，无有效属性时为 null
     */
    private String extractArtifactId(Attributes attributes) {
        // 尝试多个可能的artifactId属性
        String[] artifactIdKeys = {
                "Implementation-Title",
                "Bundle-SymbolicName",
                "Bundle-Name",
                "Specification-Title",
                "Automatic-Module-Name"
        };

        for (String key : artifactIdKeys) {
            String value = attributes.getValue(key);

            if (value != null && !value.trim().isEmpty()) {
                value = value.trim();

                // 清理Bundle-SymbolicName中的版本信息
                if ("Bundle-SymbolicName".equals(key) && value.contains(";"))
                    value = value.split(";")[0].trim();

                // 如果包含点号，可能是完整的包名，提取最后一部分作为artifactId
                if (value.contains(".") && !value.startsWith("org.") && !value.startsWith("com.")) {
                    String[] parts = value.split("\\.");

                    if (parts.length > 1)
                        return parts[parts.length - 1];
                }

                return value;
            }
        }

        return null;
    }

    /**
     * 从MANIFEST.MF属性中提取version。
     * <p>依次检查 Implementation-Version、Bundle-Version、Specification-Version，
     * 取首个非空值并去除两端空白，不进行严格版本校验；没有值时返回 null。
     *
     * @param attributes 非空 Manifest 主属性
     * @return 首个非空版本属性，缺失时为 null
     */
    private String extractVersion(Attributes attributes) {
        // 尝试多个可能的version属性
        String[] versionKeys = {
                "Implementation-Version",
                "Bundle-Version",
                "Specification-Version"
        };

        for (String key : versionKeys) {
            String value = attributes.getValue(key);

            if (value != null && !value.trim().isEmpty()) {
                value = value.trim();

                // 清理Bundle-Version中的额外信息
                if ("Bundle-Version".equals(key) && value.contains(".")) {
                    if (value.matches("^\\d+(\\.\\d+)*.*")) // 保留标准的版本格式 (x.y.z)
                        return value;
                }

                return value;
            }
        }

        return null;
    }

    /**
     * 从artifactId推断groupId（增强版本）。
     * <p>对常见域名前缀的点分名称取最后一段之前的内容，否则调用常见组件映射。
     *
     * @param artifactId 非空组件名称，用于匹配或推断组名
     * @return 推断组名或 unknown
     */
    private String inferGroupIdFromArtifactId(String artifactId) {
        // 如果artifactId包含点号，可能是完整的包名
        if (artifactId.contains(".")) {
            String[] parts = artifactId.split("\\.");

            if (parts.length > 1) {
                // 如果是标准的包名格式，提取groupId部分
                if (parts[0].matches("^(org|com|net|io|javax|jakarta)$"))
                    return String.join(".", java.util.Arrays.copyOf(parts, parts.length - 1));
            }
        }

        return inferGroupId(artifactId); // 使用现有的inferGroupId方法
    }

    /**
     * 从文件名解析依赖信息。
     * <p>正则匹配组件名和数字开头版本，再以组件名推断组名。
     * 无法匹配时仍创建记录，组名和版本为 unknown，组件名为去掉 .jar 的文件名。
     *
     * @param jarPath 归档路径或发现来源标记
     * @param jarName 归档文件名
     * @return 从文件名构造的非空依赖记录
     */
    private DependencyInfo parseFromFileName(String jarPath, String jarName) {
        Matcher matcher = MAVEN_JAR_PATTERN.matcher(jarName);

        if (matcher.find()) {
            String artifactId = matcher.group(1);
            String version = matcher.group(2);

            // 根据常见的artifactId推断groupId
            String groupId = inferGroupId(artifactId);

            return new DependencyInfo(groupId, artifactId, version, jarPath);
        }

        // 如果无法解析，创建一个基本的依赖信息
        return new DependencyInfo("unknown", jarName.replace(".jar", ""), "unknown", jarPath);
    }

    /**
     * 根据artifactId推断groupId。
     * <p>使用区分大小写的前缀及子串规则按代码顺序返回猜测值，未识别时返回 unknown。
     * 硬编码映射无法覆盖所有组件迁移及同名依赖。
     *
     * @param artifactId 非空组件名称，用于匹配或推断组名
     * @return 推断组名或 unknown
     */
    private String inferGroupId(String artifactId) {
        // 常见的Spring Boot依赖
        if (artifactId.startsWith("spring-boot"))
            return "org.springframework.boot";

        // Spring Framework依赖
        if (artifactId.startsWith("spring-"))
            return "org.springframework";

        // Jackson依赖
        if (artifactId.startsWith("jackson-")) {
            if (artifactId.contains("databind") || artifactId.contains("core") || artifactId.contains("annotations"))
                return "com.fasterxml.jackson.core";
            if (artifactId.contains("datatype"))
                return "com.fasterxml.jackson.datatype";
            if (artifactId.contains("module"))
                return "com.fasterxml.jackson.module";
            return "com.fasterxml.jackson.core";
        }

        // 日志相关依赖
        if (artifactId.startsWith("log4j"))
            return "org.apache.logging.log4j";
        if (artifactId.startsWith("slf4j"))
            return "org.slf4j";
        if (artifactId.startsWith("logback"))
            return "ch.qos.logback";
        if (artifactId.startsWith("jul-to-slf4j"))
            return "org.slf4j";
        if (artifactId.startsWith("jcl-over-slf4j"))
            return "org.slf4j";

        // Tomcat依赖
        if (artifactId.startsWith("tomcat"))
            return "org.apache.tomcat.embed";

        // 数据库依赖
        if (artifactId.startsWith("h2"))
            return "com.h2database";
        if (artifactId.startsWith("mysql"))
            return "mysql";
        if (artifactId.startsWith("postgresql"))
            return "org.postgresql";
        if (artifactId.startsWith("HikariCP"))
            return "com.zaxxer";

        // Maven依赖
        if (artifactId.startsWith("maven"))
            return "org.apache.maven";

        // JUnit测试依赖
        if (artifactId.startsWith("junit")) {
            if (artifactId.contains("jupiter"))
                return "org.junit.jupiter";
            if (artifactId.contains("platform"))
                return "org.junit.platform";
            return "junit";
        }
        if (artifactId.startsWith("mockito"))
            return "org.mockito";
        if (artifactId.startsWith("hamcrest"))
            return "org.hamcrest";
        if (artifactId.startsWith("assertj"))
            return "org.assertj";
        if (artifactId.startsWith("xmlunit"))
            return "org.xmlunit";
        if (artifactId.startsWith("jsonassert"))
            return "org.skyscreamer";

        // Apache Commons依赖
        if (artifactId.startsWith("commons-"))
            return "org.apache.commons";

        // Hibernate依赖
        if (artifactId.startsWith("hibernate")) {
            if (artifactId.contains("validator"))
                return "org.hibernate.validator";
            return "org.hibernate";
        }

        // Micrometer依赖
        if (artifactId.startsWith("micrometer"))
            return "io.micrometer";

        // SnakeYAML依赖
        if (artifactId.equals("snakeyaml"))
            return "org.yaml";

        // Validation API
        if (artifactId.startsWith("validation-api"))
            return "javax.validation";
        if (artifactId.startsWith("jakarta.validation"))
            return "jakarta.validation";

        // 其他常见依赖
        if (artifactId.startsWith("aspectj"))
            return "org.aspectj";

        // Byte Buddy依赖
        if (artifactId.startsWith("byte-buddy"))
            return "net.bytebuddy";

        // Objenesis依赖
        if (artifactId.startsWith("objenesis"))
            return "org.objenesis";

        // JAXB依赖
        if (artifactId.startsWith("jaxb"))
            return "javax.xml.bind";

        // 其他Jakarta依赖
        if (artifactId.startsWith("jakarta."))
            return "jakarta." + artifactId.split("-")[0].replace("jakarta.", "");

        // 其他javax依赖
        if (artifactId.startsWith("javax."))
            return "javax." + artifactId.split("-")[0].replace("javax.", "");

        // Netty依赖
        if (artifactId.startsWith("netty"))
            return "io.netty";

        // Reactor依赖
        if (artifactId.startsWith("reactor"))
            return "io.projectreactor";

        // 其他常见的org.apache依赖
        if (artifactId.startsWith("httpcore") || artifactId.startsWith("httpclient"))
            return "org.apache.httpcomponents";

        return "unknown";
    }

    /**
     * 从Spring Boot的BOOT-INF/lib目录收集依赖。
     * <p>当前仅输出调试日志，没有遍历嵌套归档，也不会修改传入集合。
     *
     * @param dependencies  接收候选依赖的非空可变列表，本方法向其中追加结果
     * @param processedJars 本次收集共享的可变去重集合，包含文件名或 Maven 坐标
     */
    private void collectFromBootInfLib(List<DependencyInfo> dependencies, Set<String> processedJars) {
        try {
            // 这个方法主要用于Spring Boot fat jar的情况
            // 在开发环境中可能不会用到，但保留以备将来使用
            log.debug("尝试从BOOT-INF/lib收集依赖...");
        } catch (Exception e) {
            log.debug("从BOOT-INF/lib收集依赖失败", e);
        }
    }
}
