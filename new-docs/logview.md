https://juejin.cn/post/7538646907213987890

# 开发者必备工具：用 SpringBoot 构建轻量级日志查看器，省时又省力 

生产环境出问题时，你还在用 `tail -f` 查日志吗？还在为了下载几个G的日志文件而苦恼吗？本文将手把手教你实现一个轻量级的日志管理系统，让日志查询变得简单而高效。

![](https://p6-xtjj-sign.byteimg.com/tos-cn-i-73owjymdk6/b0a753bcb2224373a84183b3baed9241~tplv-73owjymdk6-jj-mark-v1:0:0:0:0:5o6Y6YeR5oqA5pyv56S-5Yy6IEAg6ZmI56u55LiD:q75.awebp?rk3s=f64ab15b&x-expires=1793985647&x-signature=K9JP8K1J7SznrhgdvZbTR%2B5Mm5Q%3D)

![](https://p6-xtjj-sign.byteimg.com/tos-cn-i-73owjymdk6/0be2ecd68afb41bf9d95ed527b71fe88~tplv-73owjymdk6-jj-mark-v1:0:0:0:0:5o6Y6YeR5oqA5pyv56S-5Yy6IEAg6ZmI56u55LiD:q75.awebp?rk3s=f64ab15b&x-expires=1793985647&x-signature=31bm4ymFgurpq2g9V3v2b5llSAM%3D)

## 前言：为什么要自建日志查询系统？

在实际项目中，我们经常遇到这样的场景：

-   生产环境出现问题，需要快速定位错误日志
-   日志文件太大，下载耗时且占用带宽
-   需要根据时间、关键字、日志级别等条件筛选日志
-   多人协作时，都需要登录服务器查看日志

虽然有 ELK、Splunk 等成熟方案，但对于中小型项目来说，部署成本高、资源消耗大。今天我们用 Spring Boot + 纯前端技术栈，打造一个轻量级、开箱即用的日志管理系统。

## 系统设计

#### 整体架构

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   前端界面      │    │   Spring Boot   │    │   日志文件      │
│  (HTML+JS)     │◄──►│     后端        │◄──►│   (logback)     │
│                │    │                 │    │                 │
│ • 日志查询      │    │ • 文件读取      │    │ • app.log       │
│ • 条件筛选      │    │ • 内容解析      │    │ • error.log     │
│ • 在线预览      │    │ • 分页处理      │    │ • access.log    │
│ • 文件下载      │    │ • 下载服务      │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

#### 核心功能模块

**1\. 日志文件管理**：扫描、列举日志文件

**2\. 内容解析**：按行读取、正则匹配、分页处理

**3\. 条件筛选**：时间范围、关键字、日志级别

**4\. 在线预览**：实时显示、语法高亮

**5\. 文件下载**：支持原文件和筛选结果下载

## 后端实现

#### 1\. 项目结构

```
src/main/java/com/example/logviewer/
├── LogViewerApplication.java
├── config/
│   └── LogConfig.java
├── controller/
│   └── LogController.java
├── service/
│   └── LogService.java
├── dto/
│   ├── LogQueryRequest.java
│   └── LogQueryResponse.java
└── util/
    └── LogParser.java
```

#### 2\. 核心依赖 (pom.xml)

```
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-lang3</artifactId>
    </dependency>
    <dependency>
        <groupId>commons-io</groupId>
        <artifactId>commons-io</artifactId>
        <version>2.11.0</version>
    </dependency>
</dependencies>
```

#### 3\. 配置类

```
@Configuration
@ConfigurationProperties(prefix = "log.viewer")
@Data
public class LogConfig {
    
    /**
     * 日志文件根目录
     */
    private String logPath = "./logs";
    
    /**
     * 允许访问的日志文件扩展名
     */
    private List<String> allowedExtensions = Arrays.asList(".log", ".txt");
    
    /**
     * 单次查询最大行数
     */
    private int maxLines = 1000;
    
    /**
     * 文件最大大小（MB）
     */
    private long maxFileSize = 100;
    
    /**
     * 是否启用安全检查
     */
    private boolean enableSecurity = true;
}
```

#### 4\. 数据传输对象

```
@Data
public class LogQueryRequest {
    
    @NotBlank(message = "文件名不能为空")
    private String fileName;
    
    /**
     * 页码，从1开始
     */
    @Min(value = 1, message = "页码必须大于0")
    private int page = 1;
    
    /**
     * 每页行数
     */
    @Min(value = 1, message = "每页行数必须大于0")
    @Max(value = 1000, message = "每页行数不能超过1000")
    private int pageSize = 100;
    
    /**
     * 关键字搜索
     */
    private String keyword;
    
    /**
     * 日志级别过滤
     */
    private String level;
    
    /**
     * 开始时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    
    /**
     * 结束时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    
    /**
     * 是否倒序
     */
    private boolean reverse = true;
}

@Data
public class LogQueryResponse {
    
    /**
     * 日志内容列表
     */
    private List<String> lines;
    
    /**
     * 总行数
     */
    private long totalLines;
    
    /**
     * 当前页码
     */
    private int currentPage;
    
    /**
     * 总页数
     */
    private int totalPages;
    
    /**
     * 文件大小（字节）
     */
    private long fileSize;
    
    /**
     * 最后修改时间
     */
    private LocalDateTime lastModified;
}
```

#### 5\. 日志解析工具类

```
@Component
public class LogParser {
    
    private static final Pattern LOG_PATTERN = Pattern.compile(
        "(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\w+)\s+(.*)")
    );
    
    /**
     * 解析日志行，提取时间和级别
     */
    public LogLineInfo parseLine(String line) {
        Matcher matcher = LOG_PATTERN.matcher(line);
        if (matcher.find()) {
            String timestamp = matcher.group(1);
            String level = matcher.group(2);
            String content = matcher.group(3);
            
            LocalDateTime dateTime = parseTimestamp(timestamp);
            return new LogLineInfo(dateTime, level, content, line);
        }
        
        // 如果不匹配标准格式，返回原始行
        return new LogLineInfo(null, null, line, line);
    }
    
    private LocalDateTime parseTimestamp(String timestamp) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
            return LocalDateTime.parse(timestamp, formatter);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Data
    @AllArgsConstructor
    public static class LogLineInfo {
        private LocalDateTime timestamp;
        private String level;
        private String content;
        private String originalLine;
        
        public boolean matchesFilter(LogQueryRequest request) {
            // 时间范围过滤
            if (timestamp != null) {
                if (request.getStartTime() != null && timestamp.isBefore(request.getStartTime())) {
                    return false;
                }
                if (request.getEndTime() != null && timestamp.isAfter(request.getEndTime())) {
                    return false;
                }
            }
            
            // 日志级别过滤
            if (StringUtils.isNotBlank(request.getLevel()) && 
                !StringUtils.equalsIgnoreCase(level, request.getLevel())) {
                return false;
            }
            
            // 关键字过滤
            if (StringUtils.isNotBlank(request.getKeyword()) && 
                !StringUtils.containsIgnoreCase(originalLine, request.getKeyword())) {
                return false;
            }
            
            return true;
        }
    }
}
```

#### 6\. 核心服务类

```
@Service
@Slf4j
public class LogService {
    
    @Autowired
    private LogConfig logConfig;
    
    @Autowired
    private LogParser logParser;
    
    /**
     * 获取日志文件列表
     */
    public List<Map<String, Object>> getLogFiles() {
        File logDir = new File(logConfig.getLogPath());
        if (!logDir.exists() || !logDir.isDirectory()) {
            return Collections.emptyList();
        }
        
        return Arrays.stream(logDir.listFiles())
            .filter(this::isValidLogFile)
            .map(this::fileToMap)
            .sorted((a, b) -> ((Long)b.get("lastModified")).compareTo((Long)a.get("lastModified")))
            .collect(Collectors.toList());
    }
    
    /**
     * 查询日志内容
     */
    public LogQueryResponse queryLogs(LogQueryRequest request) {
        File logFile = getLogFile(request.getFileName());
        validateFile(logFile);
        
        try {
            List<String> allLines = FileUtils.readLines(logFile, StandardCharsets.UTF_8);
            
            // 过滤日志行
            List<String> filteredLines = filterLines(allLines, request);
            
            // 倒序处理
            if (request.isReverse()) {
                Collections.reverse(filteredLines);
            }
            
            // 分页处理
            int totalLines = filteredLines.size();
            int totalPages = (int) Math.ceil((double) totalLines / request.getPageSize());
            int startIndex = (request.getPage() - 1) * request.getPageSize();
            int endIndex = Math.min(startIndex + request.getPageSize(), totalLines);
            
            List<String> pageLines = filteredLines.subList(startIndex, endIndex);
            
            LogQueryResponse response = new LogQueryResponse();
            response.setLines(pageLines);
            response.setTotalLines(totalLines);
            response.setCurrentPage(request.getPage());
            response.setTotalPages(totalPages);
            response.setFileSize(logFile.length());
            response.setLastModified(
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(logFile.lastModified()), 
                    ZoneId.systemDefault()
                )
            );
            
            return response;
            
        } catch (IOException e) {
            log.error("读取日志文件失败: {}", logFile.getAbsolutePath(), e);
            throw new RuntimeException("读取日志文件失败", e);
        }
    }
    
    /**
     * 下载日志文件
     */
    public void downloadLog(String fileName, LogQueryRequest request, HttpServletResponse response) {
        File logFile = getLogFile(fileName);
        validateFile(logFile);
        
        try {
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", 
                "attachment; filename=" + URLEncoder.encode(fileName, "UTF-8"));
            
            if (hasFilter(request)) {
                // 下载过滤后的内容
                List<String> allLines = FileUtils.readLines(logFile, StandardCharsets.UTF_8);
                List<String> filteredLines = filterLines(allLines, request);
                
                try (PrintWriter writer = response.getWriter()) {
                    for (String line : filteredLines) {
                        writer.println(line);
                    }
                }
            } else {
                // 下载原文件
                response.setContentLengthLong(logFile.length());
                try (InputStream inputStream = new FileInputStream(logFile);
                     OutputStream outputStream = response.getOutputStream()) {
                    IOUtils.copy(inputStream, outputStream);
                }
            }
            
        } catch (IOException e) {
            log.error("下载日志文件失败: {}", logFile.getAbsolutePath(), e);
            throw new RuntimeException("下载日志文件失败", e);
        }
    }
    
    private List<String> filterLines(List<String> lines, LogQueryRequest request) {
        if (!hasFilter(request)) {
            return lines;
        }
        
        return lines.stream()
            .map(logParser::parseLine)
            .filter(lineInfo -> lineInfo.matchesFilter(request))
            .map(LogParser.LogLineInfo::getOriginalLine)
            .collect(Collectors.toList());
    }
    
    private boolean hasFilter(LogQueryRequest request) {
        return StringUtils.isNotBlank(request.getKeyword()) ||
               StringUtils.isNotBlank(request.getLevel()) ||
               request.getStartTime() != null ||
               request.getEndTime() != null;
    }
    
    private File getLogFile(String fileName) {
        // 安全检查：防止路径遍历攻击
        if (logConfig.isEnableSecurity()) {
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\")) {
                throw new IllegalArgumentException("非法的文件名");
            }
        }
        
        return new File(logConfig.getLogPath(), fileName);
    }
    
    private void validateFile(File file) {
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在");
        }
        
        if (!file.isFile()) {
            throw new IllegalArgumentException("不是有效的文件");
        }
        
        if (!isValidLogFile(file)) {
            throw new IllegalArgumentException("不支持的文件类型");
        }
        
        long fileSizeMB = file.length() / (1024 * 1024);
        if (fileSizeMB > logConfig.getMaxFileSize()) {
            throw new IllegalArgumentException(
                String.format("文件过大，超过限制 %dMB", logConfig.getMaxFileSize())
            );
        }
    }
    
    private boolean isValidLogFile(File file) {
        String fileName = file.getName().toLowerCase();
        return logConfig.getAllowedExtensions().stream()
            .anyMatch(fileName::endsWith);
    }
    
    private Map<String, Object> fileToMap(File file) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", file.getName());
        map.put("size", file.length());
        map.put("lastModified", file.lastModified());
        map.put("readable", file.canRead());
        return map;
    }
}
```

#### 7\. 日志文件实时监控

```
/**
 * 日志实时监控服务
 * 监控日志文件变化，实时推送新增日志内容
 */
@Slf4j
@Service
public class LogMonitorService implements InitializingBean, DisposableBean {

    @Autowired
    private LogConfig logConfig;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private LogParser logParser;

    // 文件监控服务
    private WatchService watchService;
    
    // 线程池
    private ScheduledExecutorService executorService;
    
    // 存储每个文件的读取位置
    private final Map<String, Long> filePositions = new ConcurrentHashMap<>();
    
    // 当前监控的文件
    private volatile String currentMonitorFile;
    
    // 监控状态
    private volatile boolean monitoring = false;

    @Override
    public void afterPropertiesSet() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            executorService = Executors.newScheduledThreadPool(2);
            
            // 注册日志目录监控
            Path logPath = Paths.get(logConfig.getLogPath());
            if (Files.exists(logPath)) {
                logPath.register(watchService, 
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);
                
                // 启动文件监控线程
                executorService.submit(this::watchFiles);
                log.info("日志文件监控服务已启动，监控目录: {}", logPath);
            }
        } catch (Exception e) {
            log.error("初始化日志监控服务失败", e);
        }
    }

    @Override
    public void destroy() {
        monitoring = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (Exception e) {
                log.error("关闭文件监控服务失败", e);
            }
        }
        if (executorService != null) {
            executorService.shutdown();
        }
        log.info("日志监控服务已关闭");
    }

    /**
     * 开始监控指定文件
     * @param fileName 文件名
     */
    public void startMonitoring(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return;
        }
        
        currentMonitorFile = fileName;
        monitoring = true;
        
        // 初始化文件读取位置
        File file = new File(logConfig.getLogPath(), fileName);
        if (file.exists()) {
            filePositions.put(fileName, file.length());
        }
        
        log.info("开始监控日志文件: {}", fileName);
        
        // 发送监控开始消息
        messagingTemplate.convertAndSend("/topic/log-monitor", 
            Map.of("type", "monitor_started", "fileName", fileName));
    }

    /**
     * 停止监控
     */
    public void stopMonitoring() {
        monitoring = false;
        currentMonitorFile = null;
        
        log.info("停止日志文件监控");
        
        // 发送监控停止消息
        messagingTemplate.convertAndSend("/topic/log-monitor", 
            Map.of("type", "monitor_stopped"));
    }

    /**
     * 文件监控线程
     */
    private void watchFiles() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = watchService.take();
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path fileName = ev.context();
                    
                    if (monitoring && currentMonitorFile != null && 
                        fileName.toString().equals(currentMonitorFile)) {
                        
                        // 延迟处理，避免文件正在写入
                        executorService.schedule(() -> processFileChange(currentMonitorFile), 
                            100, TimeUnit.MILLISECONDS);
                    }
                }
                
                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("文件监控异常", e);
            }
        }
    }

    /**
     * 处理文件变化
     * @param fileName 文件名
     */
    private void processFileChange(String fileName) {
        try {
            File file = new File(logConfig.getLogPath(), fileName);
            if (!file.exists()) {
                return;
            }
            
            long currentLength = file.length();
            long lastPosition = filePositions.getOrDefault(fileName, 0L);
            
            // 如果文件被截断（如日志轮转），重置位置
            if (currentLength < lastPosition) {
                lastPosition = 0L;
            }
            
            // 如果有新内容
            if (currentLength > lastPosition) {
                String newContent = readNewContent(file, lastPosition, currentLength);
                if (newContent != null && !newContent.trim().isEmpty()) {
                    // 解析新日志行
                    String[] lines = newContent.split("\n");
                    for (String line : lines) {
                        if (!line.trim().isEmpty()) {
                            sendLogLine(fileName, line);
                        }
                    }
                }
                
                // 更新文件位置
                filePositions.put(fileName, currentLength);
            }
        } catch (Exception e) {
            log.error("处理文件变化失败: {}", fileName, e);
        }
    }

    /**
     * 读取文件新增内容
     * @param file 文件
     * @param startPosition 开始位置
     * @param endPosition 结束位置
     * @return 新增内容
     */
    private String readNewContent(File file, long startPosition, long endPosition) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(startPosition);
            
            long length = endPosition - startPosition;
            if (length > 1024 * 1024) { // 限制单次读取大小为1MB
                length = 1024 * 1024;
            }
            
            byte[] buffer = new byte[(int) length];
            int bytesRead = raf.read(buffer);
            
            if (bytesRead > 0) {
                return new String(buffer, 0, bytesRead, "UTF-8");
            }
        } catch (Exception e) {
            log.error("读取文件内容失败: {}", file.getName(), e);
        }
        return null;
    }

    /**
     * 发送日志行到WebSocket客户端
     * @param fileName 文件名
     * @param logLine 日志行
     */
    private void sendLogLine(String fileName, String logLine) {
        try {
            // 解析日志行
            LogParser.LogLineInfo lineInfo = logParser.parseLine(logLine);
            
            // 构建消息
            Map<String, Object> message = Map.of(
                "type", "new_log_line",
                "fileName", fileName,
                "content", logLine,
                "timestamp", lineInfo.getTimestamp() != null ? lineInfo.getTimestamp().toString() : "",
                "level", lineInfo.getLevel() != null ? lineInfo.getLevel() : "",
                "rawContent", lineInfo.getContent() != null ? lineInfo.getContent() : logLine
            );
            
            // 发送到WebSocket客户端
            messagingTemplate.convertAndSend("/topic/log-monitor", message);
            
        } catch (Exception e) {
            log.error("发送日志行失败", e);
        }
    }

    /**
     * 获取当前监控状态
     * @return 监控状态信息
     */
    public Map<String, Object> getMonitorStatus() {
        return Map.of(
            "monitoring", monitoring,
            "currentFile", currentMonitorFile != null ? currentMonitorFile : "",
            "monitoredFiles", filePositions.size()
        );
    }
}
```

#### 8\. 控制器

```
@RestController
@RequestMapping("/api/logs")
@Slf4j
public class LogController {
    
    @Autowired
    private LogService logService;
    
    /**
     * 获取日志文件列表
     */
    @GetMapping("/files")
    public ResponseEntity<List<Map<String, Object>>> getLogFiles() {
        try {
            List<Map<String, Object>> files = logService.getLogFiles();
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            log.error("获取日志文件列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * 查询日志内容
     */
    @PostMapping("/query")
    public ResponseEntity<LogQueryResponse> queryLogs(@Valid @RequestBody LogQueryRequest request) {
        try {
            LogQueryResponse response = logService.queryLogs(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("查询参数错误: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("查询日志失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * 下载日志文件
     */
    @GetMapping("/download/{fileName}")
    public void downloadLog(
            @PathVariable String fileName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            HttpServletResponse response) {
        
        try {
            LogQueryRequest request = new LogQueryRequest();
            request.setFileName(fileName);
            request.setKeyword(keyword);
            request.setLevel(level);
            request.setStartTime(startTime);
            request.setEndTime(endTime);
            
            logService.downloadLog(fileName, request, response);
            
        } catch (IllegalArgumentException e) {
            log.warn("下载参数错误: {}", e.getMessage());
            response.setStatus(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            log.error("下载日志失败", e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }
}
```
 
## 配置文件

#### application.yml

```
server:
  port: 8080
  servlet:
    context-path: /

spring:
  application:
    name: log-viewer
  web:
    resources:
      static-locations: classpath:/static/

# 日志查看器配置
log:
  viewer:
    log-path: ./logs  # 日志文件目录
    allowed-extensions:
      - .log
      - .txt
    max-lines: 1000   # 单次查询最大行数
    max-file-size: 100  # 文件最大大小(MB)
    enable-security: true  # 启用安全检查

# 日志配置
logging:
  level:
    com.example.logviewer: DEBUG
  pattern:
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n"
  file:
    name: ./logs/app.log
  logback:
    rollingpolicy:
      max-file-size: 10MB
      max-history: 30
```

## 部署与使用

#### 1\. 快速启动

```
# 克隆项目
git clone <your-repo-url>
cd log-viewer

# 编译打包
mvn clean package -DskipTests

# 启动应用
java -jar target/log-viewer-1.0.0.jar

# 访问系统
open http://localhost:8080
```



#### 3\. 生产环境配置

```
# application-prod.yml
log:
  viewer:
    log-path: /var/log/myapp
    max-file-size: 500
    enable-security: true

logging:
  level:
    root: WARN
    com.example.logviewer: INFO
```

## 功能特性

#### 已实现功能

-   📁 日志文件列表展示
-   🔍 关键字搜索
-   📅 时间范围过滤
-   🏷️ 日志级别筛选
-   📄 分页浏览
-   💾 文件下载（原文件/筛选结果）
-   🎨 语法高亮
-   📱 响应式设计
-   🔒 安全防护（路径遍历攻击防护）
-   📄 实时日志监控（WebSocket）

#### 后续可扩展功能

-   日志统计分析
-   多文件合并查看
-   正则表达式搜索
-   用户权限管理
-   日志告警功能

## 性能优化建议

#### 1\. 大文件处理

```
// 使用 RandomAccessFile 实现大文件分页读取
public List<String> readFileByPage(File file, int page, int pageSize) {
    List<String> lines = new ArrayList<>();
    try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
        // 计算起始位置
        long startPos = calculateStartPosition(raf, page, pageSize);
        raf.seek(startPos);
        
        // 读取指定行数
        String line;
        int count = 0;
        while ((line = raf.readLine()) != null && count < pageSize) {
            lines.add(new String(line.getBytes("ISO-8859-1"), "UTF-8"));
            count++;
        }
    } catch (IOException e) {
        log.error("读取文件失败", e);
    }
    return lines;
}
```

#### 2\. 异步处理

```
@Async
public CompletableFuture<LogQueryResponse> queryLogsAsync(LogQueryRequest request) {
    return CompletableFuture.supplyAsync(() -> {
        return queryLogs(request);
    });
}
```

## 总结

本文实现了一个功能完整的日志查询系统，具有以下特点：

**1\. 轻量级**：无需复杂的部署，Spring Boot + 静态页面即可运行

**2\. 功能完整**：支持搜索、过滤、分页、下载等核心功能

**3\. 用户友好**：现代化的UI设计，响应式布局

**4\. 安全可靠**：包含安全检查和错误处理

**5\. 易于扩展**：模块化设计，便于添加新功能

这个系统特别适合中小型项目的日志管理需求，可以显著提升问题排查效率。

你可以根据实际需求进行定制和扩展，比如添加实时监控、统计分析等功能。

[github.com/yuboon/java…](https://link.juejin.cn/?target=https%3A%2F%2Fgithub.com%2Fyuboon%2Fjava-examples%2Ftree%2Fmaster%2Fspringboot-logview "https://github.com/yuboon/java-examples/tree/master/springboot-logview")