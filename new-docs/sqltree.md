# 自研运行时 SQL 调用树，3 分钟定位慢 SQL！

https://juejin.cn/post/7539448404612694026

在复杂的业务系统中，一个接口往往会执行多条SQL，如何直观地看到这些SQL的调用关系和执行情况？本文将使用SpringBoot + MyBatis拦截器构建一个SQL调用树可视化系统。

![](https://p3-xtjj-sign.byteimg.com/tos-cn-i-73owjymdk6/b9b3aafb5c544709a40174f421a22f25~tplv-73owjymdk6-jj-mark-v1:0:0:0:0:5o6Y6YeR5oqA5pyv56S-5Yy6IEAg6ZmI56u55LiD:q75.awebp?rk3s=f64ab15b&x-expires=1794164718&x-signature=8eHvLJAAJf3EvkFN%2FR7Qa8QmfpM%3D)

![](https://p3-xtjj-sign.byteimg.com/tos-cn-i-73owjymdk6/f245782638e14d59993cdd7bdda5ab57~tplv-73owjymdk6-jj-mark-v1:0:0:0:0:5o6Y6YeR5oqA5pyv56S-5Yy6IEAg6ZmI56u55LiD:q75.awebp?rk3s=f64ab15b&x-expires=1794164718&x-signature=amq1QRIMz0MChYtGPb%2BuiGHfnoM%3D)

## 项目背景

在日常开发中，我们经常遇到这样的场景：

-   **复杂查询链路**：一个用户详情接口可能涉及用户基本信息、订单列表、订单详情等多个查询
-   **性能问题排查**：系统响应慢，需要快速定位是哪个SQL影响了性能
-   **开发调试需求**：希望能直观地看到SQL的执行顺序和层次关系

基于这些需求，实现了一个基于SpringBoot + MyBatis的SQL调用树可视化系统。

## 系统功能特性

该系统具有以下核心功能：

### 核心功能

-   **MyBatis拦截器**：通过拦截器机制捕获SQL执行过程，无需修改业务代码
-   **调用树构建**：自动构建SQL调用的层次关系
-   **可视化展示**：使用D3.js实现树形结构的可视化展示
-   **性能监控**：记录SQL执行时间，自动标识慢SQL
-   **统计分析**：提供SQL执行统计信息和性能分析
-   **数据管理**：支持数据的查询、清理和导出

### 技术实现

-   **后端技术**：Spring Boot 3.4.5 + MyBatis 3.0.3 + H2数据库
-   **前端技术**：HTML5 + Tailwind CSS + D3.js v7
-   **配置管理**：支持动态配置慢SQL阈值等参数

## 项目结构

### 技术栈

**后端技术栈**

-   **Spring Boot 3.4.5**：应用框架
-   **MyBatis 3.0.3**：数据访问层和拦截器
-   **H2 Database**：内存数据库（演示用）
-   **Lombok**：简化代码编写
-   **Jackson**：JSON序列化

**前端技术栈**

-   **HTML5 + Tailwind CSS**：页面结构和样式
-   **D3.js v7**：数据可视化
-   **Font Awesome**：图标库
-   **原生JavaScript**：前端交互逻辑

### 项目目录结构

```
springboot-sql-tree/
├── src/main/java/com/example/sqltree/
│   ├── SqlTreeApplication.java          # 启动类
│   ├── SqlInterceptor.java              # MyBatis拦截器
│   ├── SqlCallTreeContext.java          # 调用树上下文管理
│   ├── SqlNode.java                     # SQL节点数据模型
│   ├── SqlTreeController.java           # REST API控制器
│   ├── DemoController.java              # 演示API
│   ├── UserService.java                 # 用户服务（演示用）
│   ├── UserMapper.java                  # 用户数据访问
│   └── OrderMapper.java                 # 订单数据访问
├── src/main/resources/
│   ├── application.yml                  # 应用配置
│   ├── schema.sql                       # 数据库表结构
│   ├── data.sql                         # 示例数据
│   └── static/
│       ├── index.html                   # 前端页面
│       └── sql-tree.js                  # 前端JavaScript
└── pom.xml                              # Maven配置
```

## 核心实现详解

### 1\. MyBatis拦截器：零侵入的核心

这是整个系统的**核心组件**，通过MyBatis的插件机制实现SQL执行的无感知拦截：

```
@Component
@Intercepts({
    @Signature(type = Executor.class, method = "query", args = {
        MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
    }),
    @Signature(type = Executor.class, method = "update", args = {
        MappedStatement.class, Object.class
    })
})
public class SqlInterceptor implements Interceptor {
    
    @Autowired
    private SqlCallTreeContext sqlCallTreeContext;
    
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 检查是否启用追踪
        if (!sqlCallTreeContext.isTraceEnabled()) {
            return invocation.proceed();
        }
        
        long startTime = System.currentTimeMillis();
        Object[] args = invocation.getArgs();
        MappedStatement mappedStatement = (MappedStatement) args[0];
        Object parameter = args[1];
        
        // 获取SQL信息
        BoundSql boundSql = mappedStatement.getBoundSql(parameter);
        String sql = boundSql.getSql();
        String sqlType = mappedStatement.getSqlCommandType().name();
        
        // 获取调用栈信息
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String serviceName = extractServiceName(stackTrace);
        String methodName = extractMethodName(stackTrace);
        
        // 创建SQL节点
        SqlNode sqlNode = SqlNode.builder()
            .nodeId(UUID.randomUUID().toString())
            .sql(formatSql(sql))
            .sqlType(sqlType)
            .threadName(Thread.currentThread().getName())
            .serviceName(serviceName)
            .methodName(methodName)
            .startTime(LocalDateTime.now())
            .parameters(extractParameters(boundSql, parameter))
            .depth(sqlCallTreeContext.getCurrentDepth() + 1)
            .build();
        
        // 进入SQL调用
        sqlCallTreeContext.enter(sqlNode);
        
        try {
            // 执行SQL
            Object result = invocation.proceed();
            
            // 记录执行结果
            long executionTime = System.currentTimeMillis() - startTime;
            int affectedRows = calculateAffectedRows(result, sqlType);
            
            sqlCallTreeContext.exit(sqlNode, affectedRows, null);
            
            return result;
            
        } catch (Exception e) {
            // 记录异常信息
            sqlCallTreeContext.exit(sqlNode, 0, e.getMessage());
            throw e;
        }
    }
    
    private String extractServiceName(StackTraceElement[] stackTrace) {
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (className.contains("Service") && !className.contains("$")) {
                return className.substring(className.lastIndexOf('.') + 1);
            }
        }
        return "Unknown";
    }
    
    private String extractMethodName(StackTraceElement[] stackTrace) {
        for (StackTraceElement element : stackTrace) {
            if (element.getClassName().contains("Service")) {
                return element.getMethodName();
            }
        }
        return "unknown";
    }
    
    private int calculateAffectedRows(Object result, String sqlType) {
        if ("SELECT".equals(sqlType) && result instanceof List) {
            return ((List<?>) result).size();
        } else if (result instanceof Integer) {
            return (Integer) result;
        }
        return 0;
    }
}
```

**关键特性**：

-   🎯 **精准拦截**：同时拦截查询和更新操作
-   ⚡ **性能优化**：可动态开关，避免生产环境性能影响
-   🔒 **异常安全**：确保业务逻辑不受监控影响
-   📊 **丰富信息**：自动提取Service调用信息和执行统计

### 2\. 调用树上下文管理器：线程安全的数据管理

`SqlCallTreeContext`负责管理SQL调用树的构建和存储，采用线程安全的设计：

```
@Component
public class SqlCallTreeContext {
    
    // 线程本地存储
    private final ThreadLocal<Stack<SqlNode>> callStack = new ThreadLocal<Stack<SqlNode>>() {
        @Override
        protected Stack<SqlNode> initialValue() {
            return new Stack<>();
        }
    };
    
    private final ThreadLocal<List<SqlNode>> rootNodes = new ThreadLocal<List<SqlNode>>() {
        @Override
        protected List<SqlNode> initialValue() {
            return new ArrayList<>();
        }
    };
    
    // 全局会话存储
    private final Map<String, List<SqlNode>> globalSessions = new ConcurrentHashMap<>();
    
    // 统计信息
    private final AtomicLong totalSqlCount = new AtomicLong(0);
    private final AtomicLong slowSqlCount = new AtomicLong(0);
    private final AtomicLong errorSqlCount = new AtomicLong(0);
    private final AtomicLong totalExecutionTime = new AtomicLong(0);
    
    // 配置参数
    private volatile long slowSqlThreshold = 1000; // 慢SQL阈值（毫秒）
    private volatile boolean traceEnabled = true; // 追踪开关
    
    /**
     * 进入SQL调用
     */
    public SqlNode enter(SqlNode sqlNode) {
        if (!traceEnabled) {
            return sqlNode;
        }
        
        Stack<SqlNode> stack = callStack.get();
        
        // 设置深度
        sqlNode.setDepth(stack.size() + 1);
        
        // 建立父子关系
        if (!stack.isEmpty()) {
            SqlNode parent = stack.peek();
            parent.addChild(sqlNode);
            sqlNode.setParentId(parent.getNodeId());
        } else {
            // 根节点
            rootNodes.get().add(sqlNode);
        }
        
        // 压入栈
        stack.push(sqlNode);
        
        return sqlNode;
    }
    
    /**
     * 退出SQL调用
     */
    public void exit(SqlNode sqlNode, int affectedRows, String errorMessage) {
        if (!traceEnabled) {
            return;
        }
        
        // 设置结束时间和结果
        sqlNode.setEndTime(LocalDateTime.now());
        sqlNode.setAffectedRows(affectedRows);
        sqlNode.setErrorMessage(errorMessage);
        
        // 计算执行时间
        long executionTime = Duration.between(sqlNode.getStartTime(), sqlNode.getEndTime()).toMillis();
        sqlNode.setExecutionTime(executionTime);
        
        // 标记慢SQL
        if (executionTime > slowSqlThreshold) {
            sqlNode.setSlowSql(true);
            slowSqlCount.incrementAndGet();
        }
        
        // 标记错误SQL
        if (errorMessage != null) {
            errorSqlCount.incrementAndGet();
        }
        
        // 更新统计
        totalSqlCount.incrementAndGet();
        totalExecutionTime.addAndGet(executionTime);
        
        // 弹出栈
        Stack<SqlNode> stack = callStack.get();
        if (!stack.isEmpty()) {
            stack.pop();
            
            // 如果栈为空，说明调用树完成，保存到全局会话
            if (stack.isEmpty()) {
                String sessionKey = generateSessionKey();
                globalSessions.put(sessionKey, new ArrayList<>(rootNodes.get()));
                rootNodes.get().clear();
            }
        }
    }
    
    /**
     * 获取当前调用深度
     */
    public int getCurrentDepth() {
        return callStack.get().size();
    }
    
    /**
     * 获取当前线程的根节点
     */
    public List<SqlNode> getRootNodes() {
        return new ArrayList<>(rootNodes.get());
    }
    
    /**
     * 获取所有会话
     */
    public Map<String, List<SqlNode>> getAllSessions() {
        return new HashMap<>(globalSessions);
    }
    
    /**
     * 清理会话数据
     */
    public void clearSessions() {
        globalSessions.clear();
        rootNodes.get().clear();
        callStack.get().clear();
    }
    
    /**
     * 生成会话键
     */
    private String generateSessionKey() {
        return Thread.currentThread().getName() + "_" + System.currentTimeMillis();
    }
    
    /**
     * 获取统计信息
     */
    public SqlStatistics getStatistics() {
        return SqlStatistics.builder()
            .totalSqlCount(totalSqlCount.get())
            .slowSqlCount(slowSqlCount.get())
            .errorSqlCount(errorSqlCount.get())
            .averageExecutionTime(totalSqlCount.get() > 0 ? 
                totalExecutionTime.get() / totalSqlCount.get() : 0)
            .build();
    }
    
    // Getter和Setter方法
    public boolean isTraceEnabled() {
        return traceEnabled;
    }
    
    public void setTraceEnabled(boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
    }
    
    public long getSlowSqlThreshold() {
        return slowSqlThreshold;
    }
    
    public void setSlowSqlThreshold(long slowSqlThreshold) {
        this.slowSqlThreshold = slowSqlThreshold;
    }
}
```

**设计亮点**：

-   🧵 **线程安全**：使用ThreadLocal确保多线程环境下的数据隔离
-   🌳 **智能建树**：自动识别父子关系，构建完整调用树
-   📊 **实时统计**：同步更新性能统计信息

### 3\. 数据模型：完整的SQL节点信息

```
@Data
public class SqlNode {
    private String nodeId;              // 节点唯一标识
    private String sql;                 // SQL语句
    private String formattedSql;        // 格式化后的SQL
    private String sqlType;             // SQL类型
    private int depth;                  // 调用深度
    private String threadName;          // 线程名称
    private String serviceName;         // Service类名
    private String methodName;          // Service方法名
    private LocalDateTime startTime;    // 开始时间
    private LocalDateTime endTime;      // 结束时间
    private long executionTime;         // 执行耗时
    private boolean slowSql;            // 是否为慢SQL
    private int affectedRows;           // 影响行数
    private String errorMessage;        // 错误信息
    private List<Object> parameters;    // SQL参数
    private List<SqlNode> children;     // 子节点
    
    // 智能分析方法
    public boolean isSlowSql(long threshold) {
        return executionTime > threshold;
    }
    
    public int getTotalNodeCount() {
        return 1 + children.stream().mapToInt(SqlNode::getTotalNodeCount).sum();
    }
    
    public int getMaxDepth() {
        return children.isEmpty() ? depth : 
               children.stream().mapToInt(SqlNode::getMaxDepth).max().orElse(depth);
    }
}
```
 
**前端核心特性**：

-   🌳 **树形布局**：清晰展示SQL调用层次关系
-   🎨 **颜色编码**：绿色(正常)、红色(慢SQL)
-   🖱️ **交互操作**：点击节点查看详情，悬停显示提示
-   🔍 **智能筛选**：支持按执行时间、SQL类型等条件筛选
-   📊 **实时刷新**：支持自动/手动刷新数据

## 快速开始

### 环境要求

-   **Java 21+**
-   **Maven 3.6+**
-   **现代浏览器**（支持ES6+）

### 访问系统

启动成功后，可以通过以下地址访问：

-   **可视化界面**：[http://localhost:8080/index.html](https://link.juejin.cn/?target=http%3A%2F%2Flocalhost%3A8080%2Findex.html "http://localhost:8080/index.html")
-   **H2数据库控制台**：[http://localhost:8080/h2-console](https://link.juejin.cn/?target=http%3A%2F%2Flocalhost%3A8080%2Fh2-console "http://localhost:8080/h2-console")

-   -   JDBC URL: `jdbc:h2:mem:testdb`
-   用户名: `sa`
-   密码: （空）

### 项目配置

**核心依赖（pom.xml）**

```
<dependencies>
    <!-- Spring Boot 3.4.5 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <version>3.4.5</version>
    </dependency>
    
    <!-- MyBatis 3.0.3 -->
    <dependency>
        <groupId>org.mybatis.spring.boot</groupId>
        <artifactId>mybatis-spring-boot-starter</artifactId>
        <version>3.0.3</version>
    </dependency>
    
    <!-- H2 Database -->
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>
    
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

**应用配置（application.yml）**

```
server:
  port: 8080

spring:
  application:
    name: springboot-sql-tree
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password: 
    schema: classpath:schema.sql
    data: classpath:data.sql
  h2:
    console:
      enabled: true
      path: /h2-console

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.example.sqltree.entity
  configuration:
    map-underscore-to-camel-case: true
    lazy-loading-enabled: true
    cache-enabled: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
```

## 实际应用场景

### 开发调试场景

**场景1：复杂查询性能分析**

当调用 `/api/demo/user/1/detail` 接口时，系统会自动捕获以下SQL调用链：

```
UserService.getUserDetailWithOrders()
├── SELECT * FROM users WHERE id = ? (2ms)
└── SELECT * FROM orders WHERE user_id = ? (15ms)
    └── SELECT * FROM order_items WHERE order_id IN (...) (45ms)
```

通过可视化界面可以清晰看到：

-   总执行时间：62ms
-   SQL调用深度：2层
-   性能瓶颈：order\_items查询耗时最长

**场景2：慢SQL识别**

系统自动标识执行时间超过阈值（默认1000ms）的SQL：

```
{
  "nodeId": "uuid-123",
  "sql": "SELECT * FROM orders o JOIN users u ON o.user_id = u.id WHERE o.status = ?",
  "executionTime": 1250,
  "slowSql": true,
  "serviceName": "OrderService",
  "methodName": "getOrdersWithUserInfo"
}
```

### 数据监控

**统计信息示例**

```
{
  "totalSqlCount": 1247,
  "slowSqlCount": 23,
  "errorSqlCount": 5,
  "averageExecutionTime": 35.6,
  "slowSqlThreshold": 1000,
  "traceEnabled": true
}
```

**慢SQL分析报告**

系统提供按执行时间排序的慢SQL列表：

```
[  {    "sql": "SELECT COUNT(*) FROM orders WHERE created_at BETWEEN ? AND ?",    "executionTime": 2150,    "serviceName": "ReportService",    "methodName": "generateDailyReport",    "affectedRows": 1  },  {    "sql": "UPDATE users SET last_login = ? WHERE id IN (...)",    "executionTime": 1890,    "serviceName": "UserService",    "methodName": "batchUpdateLastLogin",    "affectedRows": 156  }]
```

## 技术特点

**零侵入设计**

-   基于MyBatis拦截器实现，无需修改现有业务代码
-   通过注解和配置即可启用SQL监控功能
-   支持动态开启/关闭追踪功能

**线程安全**

-   使用`ThreadLocal`确保多线程环境下的数据隔离
-   `ConcurrentHashMap`保证全局会话存储的线程安全
-   无锁设计，避免性能瓶颈

**内存友好**

-   会话级别的数据存储，避免全局数据累积
-   支持手动清理和自动过期机制
-   轻量级数据结构，内存占用小

## 总结

这个项目展示了如何结合Spring Boot生态和前端技术，构建一个实用的SQL监控工具，为日常开发和性能优化提供有力支持。

[github.com/yuboon/java…](https://link.juejin.cn/?target=https%3A%2F%2Fgithub.com%2Fyuboon%2Fjava-examples%2Ftree%2Fmaster%2Fspringboot-sql-tree "https://github.com/yuboon/java-examples/tree/master/springboot-sql-tree")