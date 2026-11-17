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
