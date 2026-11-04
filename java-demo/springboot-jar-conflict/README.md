# 痛点背景

在 Spring Boot 项目开发和运维中，**Jar 包冲突** 是让开发者最头疼的问题之一：
https://juejin.cn/post/7550282569834577974

## 常见冲突场景

**类重复**：不同依赖引入了相同的类，启动时报 `ClassCastException` 或 `NoSuchMethodError`
**版本冲突**：同一个库的不同版本混用，行为不一致，线上才暴露问题
**日志混乱**：SLF4J + Logback + Log4j 多个实现共存，日志输出异常
**驱动重复**：MySQL 5.x 和 8.x 驱动同时存在，连接异常

## 现有方案的局限

- `mvn dependency:tree`：只能编译期分析，无法反映运行时 classpath
- IDE 插件：需要人工操作，无法自动化集成
- 第三方工具：过重，难以嵌入 Spring Boot 应用

我们需要一个**轻量、可嵌入、运行时可见**的 Jar 包冲突检测工具。

# 技术方案设计

## 2.1 核心架构

```
运行时扫描 → 冲突检测 → 配置化建议 → Web 可视化
     ↓           ↓           ↓            
ClassLoader   规则引擎   模板系统    
适配器        智能分析   变量替换     
```

## 2.2 关键技术点

多环境 ClassLoader 适配

```java
public List<URL> getClasspathUrls(){
        ClassLoader classLoader=Thread.currentThread().getContextClassLoader();
        List<URL> urls=new ArrayList<>();

        // 遍历所有 ClassLoader 层级
        ClassLoader current=classLoader;
        while(current!=null){
        if(current instanceof URLClassLoader urlClassLoader){
        urls.addAll(Arrays.asList(urlClassLoader.getURLs()));
        }
        current=current.getParent();
        }

        // Spring Boot LaunchedURLClassLoader 特殊处理
        if(classLoader.getClass().getName().contains("LaunchedURLClassLoader")){
        urls.addAll(extractFromLaunchedClassLoader(classLoader));
        }

        return urls.stream().distinct().toList();
        }
```

三维冲突检测算法

```java
// 类重复检测
Map<String, List<JarInfo>>classToJarsMap=new HashMap<>();
        for(JarInfo jar:jars){
        for(String className:jar.getClasses()){
        classToJarsMap.computeIfAbsent(className,k->new ArrayList<>()).add(jar);
        }
        }

// 版本冲突检测
        Map<String, List<JarInfo>>nameToJarsMap=jars.stream()
        .collect(Collectors.groupingBy(JarInfo::getName));

// JAR 重复检测（基于签名）
        Map<String, List<JarInfo>>signatureMap=jars.stream()
        .collect(Collectors.groupingBy(this::generateJarSignature));
```

配置化规则引擎

完全摒弃硬编码，通过 YAML 配置定义所有规则：

```yaml
conflict:
  advisor:
    rules:
      slf4j-logging:
        patterns: [ ".*slf4j.*", ".*logback.*", ".*log4j.*" ]
        severity: HIGH
        advice: |
          🚨 日志框架冲突！
          当前冲突：${className}
          涉及JAR：${jarList}

          解决方案：
          1. 排除多余的日志实现
          2. 统一使用 logback-classic
          3. 配置示例：
             <exclusion>
               <groupId>org.slf4j</groupId>
               <artifactId>slf4j-simple</artifactId>
             </exclusion>
```

___

# 核心实现

## Jar 包扫描器

支持开发环境和生产环境的智能扫描：

```java

@Component
public class JarScanner {

    public List<JarInfo> scanJars() {
        List<JarInfo> jars = new ArrayList<>();
        List<URL> urls = classLoaderAdapter.getClasspathUrls();

        for (URL url : urls) {
            String path = url.getPath();

            if (shouldExclude(path)) continue;

            if (path.endsWith(".jar")) {
                // 扫描 JAR 文件
                jars.add(scanJarFile(url));
            } else if (path.contains("target/classes")) {
                // 扫描开发环境类目录
                jars.add(scanClassesDirectory(url));
            }
        }

        return jars;
    }

    private JarInfo scanJarFile(URL url) {
        try (JarFile jar = new JarFile(new File(url.toURI()))) {
            JarInfo jarInfo = new JarInfo();
            jarInfo.setName(extractJarName(jar.getName()));
            jarInfo.setVersion(extractVersion(jar));

            // 扫描所有类文件
            List<String> classes = jar.stream()
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .map(entry -> entry.getName()
                            .replace("/", ".")
                            .replace(".class", ""))
                    .toList();

            jarInfo.setClasses(classes);
            return jarInfo;
        } catch (Exception e) {
            logger.warn("Failed to scan jar: {}", url, e);
            return null;
        }
    }
}
```

## 配置化建议生成器

**核心亮点：零硬编码，完全配置驱动**

```
@Component
@ConfigurationProperties(prefix = "conflict.advisor")
public class ConflictAdvisor {
    
    private Map<String, RuleDefinition> rules = new HashMap<>();
    private List<SeverityRule> severityRules = new ArrayList<>();
    
    public void generateAdvice(List<ConflictInfo> conflicts) {
        for (ConflictInfo conflict : conflicts) {
            String identifier = extractIdentifier(conflict);
            
            // 查找匹配的规则
            for (RuleDefinition rule : rules.values()) {
                if (rule.matches(identifier)) {
                    conflict.setSeverity(rule.getSeverity());
                    conflict.setAdvice(formatAdvice(rule.getAdvice(), conflict));
                    break;
                }
            }
        }
    }
    
    private String formatAdvice(String template, ConflictInfo conflict) {
        Map<String, String> variables = buildVariables(conflict);
        
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
    
    // 支持的模板变量
    private Map<String, String> buildVariables(ConflictInfo conflict) {
        Map<String, String> variables = new HashMap<>();
        variables.put("className", conflict.getClassName());
        variables.put("conflictType", getConflictTypeText(conflict.getType()));
        variables.put("jarCount", String.valueOf(conflict.getConflictingJars().size()));
        variables.put("jars", conflict.getConflictingJars().stream()
            .map(jar -> jar.getName() + ":" + jar.getVersion())
            .collect(Collectors.joining(", ")));
        variables.put("jarList", conflict.getConflictingJars().stream()
            .map(jar -> jar.getName() + ":" + jar.getVersion())
            .collect(Collectors.joining("\n")));
        return variables;
    }
}
```

# 配置化规则系统

## 规则定义语法

```
conflict:
  advisor:
    rules:
      # 规则名称
      database-driver:
        # 匹配模式（支持正则表达式）
        patterns:
          - ".*mysql.*"
          - ".*postgresql.*"
          - ".*Driver.*"
        # 严重程度
        severity: CRITICAL
        # 建议模板（支持变量替换）
        advice: |
          🔗 数据库驱动冲突
          当前版本：${versions}
          
          解决方案：
          1. 统一驱动版本
          2. 移除不需要的数据库驱动
          3. 使用 Spring Boot 管理的版本
```

## 4.2 支持的模板变量

| 变量名               | 说明           | 示例                                  |
|-------------------|--------------|-------------------------------------|
| `${className}`    | 冲突的类名或JAR名   | `org.slf4j.Logger`                  |
| `${conflictType}` | 冲突类型         | `类重复`、`版本冲突`                        |
| `${jarCount}`     | 冲突JAR包数量     | `3`                                 |
| `${jars}`         | JAR包列表（逗号分隔） | `slf4j-api:1.7.36, slf4j-api:2.0.9` |
| `${jarList}`      | JAR包列表（换行分隔） | 用于详细展示                              |
| `${versions}`     | 版本列表         | `1.7.36, 2.0.9`                     |

## 4.3 严重程度规则

支持多维度匹配条件：

```
severity-rules:
  # 关键组件 - 严重
  - patterns: [".*logger.*", ".*driver.*", ".*datasource.*"]
    severity: CRITICAL
    conflict-types: [CLASS_DUPLICATE, VERSION_CONFLICT]
  
  # 框架组件 - 高
  - patterns: [".*spring.*", ".*hibernate.*"]
    severity: HIGH
    conflict-types: [VERSION_CONFLICT]
  
  # 大量冲突 - 中等
  - min-jar-count: 4
    severity: MEDIUM
    conflict-types: [CLASS_DUPLICATE]
```

___

# 实战效果展示

## 检测结果示例

假设项目中存在以下冲突：

**依赖配置**：

```
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-all</artifactId>
    <version>5.8.16</version>
</dependency>
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-core</artifactId>
    <version>5.8.19</version>
</dependency>
```

**检测结果**：

```
{
  "conflicts": [
    {
      "className": "cn.hutool.core.util.StrUtil",
      "type": "CLASS_DUPLICATE",
      "severity": "MEDIUM",
      "conflictingJars": [
        {"name": "hutool-all", "version": "5.8.16"},
        {"name": "hutool-core", "version": "5.8.19"}
      ],
      "advice": "工具库冲突...\n解决方案：\n1. 选择一个 hutool 版本\n2. 排除传递依赖..."
    }
  ],
  "summary": {
    "totalJars": 45,
    "conflictCount": 1,
    "scanTimeMs": 127
  }
}
```

## Web 界面效果

**概览面板**：总 JAR 数、冲突数量、扫描耗时

**严重程度分布**：CRITICAL/HIGH/MEDIUM/LOW 分类统计

**详细列表**：类名、冲突类型、涉及 JAR、修复建议

![](https://p3-xtjj-sign.byteimg.com/tos-cn-i-73owjymdk6/80a53a0bd7064ee5b372671e58ab0464~tplv-73owjymdk6-jj-mark-v1:0:0:0:0:5o6Y6YeR5oqA5pyv56S-5Yy6IEAg6ZmI56u55LiD:q75.awebp?rk3s=f64ab15b&x-expires=1793654791&x-signature=jrmjtVcRXZOjgGQDq1dOvbSAPh4%3D)

# 企业级应用场景

## 开发阶段集成

```
@Component
public class ConflictDetectionStartupRunner implements CommandLineRunner {
    
    @Override
    public void run(String... args) throws Exception {
        if (isDevelopmentEnvironment()) {
            ScanResult result = performConflictScan();
            if (result.getConflicts().size() > 0) {
                logger.warn("发现 {} 个依赖冲突，建议访问 http://localhost:8080 查看详情", 
                    result.getConflicts().size());
            }
        }
    }
}
```

## CI/CD 流水线集成

```
#!/bin/bash
# 在 CI 阶段运行冲突检测
java -jar conflict-detector.jar --mode=ci --output=report.json

# 检查冲突数量
CONFLICTS=$(cat report.json | jq '.summary.conflictCount')
if [ $CONFLICTS -gt 0 ]; then
    echo "发现 $CONFLICTS 个依赖冲突，请检查报告"
    exit 1
fi
```

# 总结

本工具通过配置化规则和运行时扫描，实现了对 Jar 包冲突的自动检测和修复建议。无论开发环境还是生产环境，都可以直观地看到冲突详情，并及时处理。