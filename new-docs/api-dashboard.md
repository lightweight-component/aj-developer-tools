# Spring Boot API 监控面板与方法级耗时监控：完整指南

本文整合本目录的 [README.md](api-dashboard/README.md) 与掘金文章 [《SpringBoot 方法级耗时监控器》](https://juejin.cn/post/7548645458047877135)，合并重复说明，覆盖设计原理、采集与聚合、配置、界面、API 和使用场景。

本文描述的是两份资料中的原始方案。Spring Boot 版本、AOP 依赖、页面路径、配置前缀和响应包装需以实际集成项目为准；文中代码用于解释算法，不是完整的可运行工程。

## 1. 项目定位

线上接口偶发变慢时，仅靠日志往往难以判断发生时间、调用频率和影响范围。异常或超时也需要结合调用次数、失败次数与耗时分析。对于单机服务和中小型项目，引入完整的分布式 APM 可能增加部署和维护成本。

本项目在 Spring Boot 应用中采集 Controller 方法执行情况，使用内存时间桶保存统计结果，通过 REST API 和 Web 面板提供查询、搜索与排序。无需额外数据库或独立监控服务，适合快速接入和临时排障。

它提供基础的方法性能观察能力；跨服务调用链、集群汇总等需求仍需要其他设施。

## 2. 功能与指标

| 类别 | 内容 |
| --- | --- |
| 调用统计 | 方法调用总数、成功次数、失败次数 |
| 耗时统计 | 总耗时、平均耗时、最大耗时、最小耗时 |
| 成功率 | 成功次数占总调用次数的百分比 |
| 时间查询 | 全量累计、最近一段时间、自定义时间范围 |
| 方法搜索 | 按方法名称关键字模糊匹配 |
| 数据展示 | 汇总卡片、方法明细表格、成功率进度条、多列排序 |
| 数据维护 | 清空全部指标、手动清理过期指标、定时清理 |

界面汇总包括监控方法数、总调用数、错误数和平均响应时间。方法列表可按调用量、平均耗时、失败次数等维度排序。

## 3. 技术栈与处理流程

原始示例使用：

- Java 17 或更高版本。
- Spring Boot 3.2.1、Spring AOP、Lombok。
- HTML、TailwindCSS、Alpine.js。
- 内存中的并发集合、原子计数器及时间桶，无外部存储依赖。

处理流程：

```text
Controller 方法调用
       ↓
AOP 记录执行耗时和异常结果
       ↓
按方法更新累计指标与分级时间桶
       ↓
REST API 按名称和时间查询、聚合
       ↓
Web 面板展示汇总与明细

定时任务 → 清理长期未更新的指标
```

文章采用 AOP，也提到可以基于拦截器实现。两种实现的拦截范围和耗时口径不同，接入时应按实际代码理解。

## 4. 分级时间桶设计

### 4.1 保存范围与粒度

| 层级 | 历史范围 | 桶粒度 | 本地 README 给出的桶数量 |
| --- | --- | --- | --- |
| 秒级 | 最近 5 分钟 | 1 秒 | 300 |
| 分钟级 | 最近 1 小时 | 1 分钟 | 60 |
| 小时级 | 最近 24 小时 | 1 小时 | 24 |
| 天级 | 最近 7 天 | 1 天 | 7 |

两份资料将其称为“分级采样”。从文章记录逻辑看，每次调用都会更新累计统计和各层时间桶，核心是分级聚合，而非仅随机抽取部分请求。

本地资料给出每个方法合计 391 个桶的设计规模。实际桶数量还与时间边界、过期清理时机和实现方式有关，不能据此认定整个进程占用固定内存。

### 4.2 单个时间桶

文章中的 `TimeBucket` 保存以下状态：

- 调用、成功、失败次数：使用 `AtomicLong`。
- 耗时总和：使用 `LongAdder`。
- 最大和最小耗时：以 `volatile long` 保存。
- 桶起始时间和最后更新时间。

记录方法通过 `synchronized` 更新计数、耗时极值和最后更新时间。原子计数与可见性修饰不等于所有字段天然构成一致快照，读取侧仍需按实际实现处理并发一致性。

计算关系为：

```text
平均耗时 = 耗时总和 / 调用次数
成功率 = 成功次数 / 调用次数 × 100%
```

调用次数为 0 时，文章示例返回 0，避免除零。

### 4.3 每个方法的指标容器

`HierarchicalMethodMetrics` 同时维护方法名称、累计统计和四组时间桶。每组桶使用 `ConcurrentHashMap<Long, TimeBucket>` 管理，以桶时间作为键。

一次记录的处理顺序为：

1. 获取当前时间，更新方法累计统计。
2. 将本次耗时和成功标记写入不同粒度的桶。
3. 清理已经超出保留范围的桶。

时间查询先选择合适的桶，再聚合为 `TimeRangeMetrics`。文章没有完整给出桶选择和快照聚合代码，这些部分需要在工程中实现。

### 4.4 查询精度

短时间窗口使用细粒度桶，较长窗口使用粗粒度桶，以平衡查询开销和历史范围。同一查询应避免把不同层级中重复记录的调用再次相加。

时间桶通常不能还原桶内每次调用的精确时间，因此范围边缘可能有聚合误差。筛选时间越长，能够保留的细节通常越少。

## 5. 方法执行采集

文章的 `MethodMetricsAspect` 对带有 `@RestController` 或 `@Controller` 的类进行环绕拦截，使用并发 Map 保存每个方法的指标。

采集步骤如下：

```text
确定方法标识
记录 nanoTime 起点
执行目标方法
    正常返回 → 标记成功
    抛出异常 → 标记失败，并重新抛出原异常
finally 中计算毫秒耗时，写入对应方法指标
```

使用 `System.nanoTime()` 计算执行时长，使用系统时间确定时间桶归属。文章将纳秒差除以 1,000,000，以毫秒保存。

需理解示例的统计口径：

- 成功表示方法没有向外抛出异常；返回业务失败对象也可能计为成功。
- Controller 内已捕获的异常不会被外围切面识别为失败。
- 方法耗时不一定等于客户端感知的完整 HTTP 请求耗时。
- 返回异步对象时，方法返回时间不一定是异步任务或响应完成时间。
- 原示例以“类简单名.方法名()”作为标识，可能合并重载方法或不同包中的同名类。
- 指标 API 自身是否被采集，应由切点或排除规则明确，避免监控请求干扰统计。

文章的 `getMetricsSnapshot()` 返回 Map 的副本，但内部指标对象仍是原对象，属于浅拷贝，不是所有指标在同一时刻的不可变快照。

## 6. API 接口

以下均为原始资料中的路径。宿主应用如设置了 context-path，应在路径前增加该前缀。

### 6.1 方法指标

```http
GET /api/metrics?startTime=1640995200000&endTime=1641000000000&methodFilter=user
```

| 参数 | 含义 |
| --- | --- |
| `startTime` | 开始时间，Unix 毫秒时间戳 |
| `endTime` | 结束时间，Unix 毫秒时间戳 |
| `methodFilter` | 方法名搜索关键字 |

文章示例在开始与结束时间均存在时查询指定范围，否则返回累计指标；方法名按忽略大小写的包含关系过滤。示例时间戳仅说明格式，实际使用应传入目标时间段。

返回数据按方法标识组织，每个方法对应一份统计。文章未完整展开所有返回字段，不应据此推定实际工程的完整 JSON 契约。

### 6.2 汇总统计

```http
GET /api/metrics/summary?startTime=1640995200000&endTime=1641000000000&methodFilter=user
```

查询条件与方法指标保持一致，供汇总卡片展示。文章省略了具体汇总实现。

汇总平均耗时应使用“各方法耗时总和 / 各方法调用总数”，不能直接对各方法平均耗时再做算术平均。

### 6.3 最近一段时间

```http
GET /api/metrics/recent/5?methodFilter=demo
GET /api/metrics/recent/60?methodFilter=user
```

`minutes` 表示向前查询的分钟数。服务端以当前时间作为结束时间，换算开始时间后复用范围查询。

### 6.4 清空与过期清理

```http
DELETE /api/metrics
DELETE /api/metrics/stale?maxAgeMs=3600000
```

前者清空全部监控指标；后者根据最大未更新时间清理过期指标。手动操作会改变后续查询结果。

### 6.5 响应包装

文章前端示例直接使用 `response.json()` 的结果作为指标对象。若宿主应用采用统一的 `{status, message, data}` 返回结构，前端应先判断 `status`，再读取 `data`，不能直接使用整个响应作为指标 Map。

## 7. Web 面板

### 时间筛选

快捷选项包括最近 5 分钟、30 分钟、1 小时、6 小时和 24 小时，也支持全量与自定义起止时间。后端根据时间范围选择合适的数据粒度。

前端将选定时间转换为毫秒时间戳，并通过 `URLSearchParams` 构建查询参数。选择预设范围时，以当前时间向前计算；自定义范围使用用户输入的起止时间。

### 方法搜索

输入关键字后模糊过滤方法名称。本地 README 指定 500ms 防抖，减少输入过程中的连续请求。

### 数据展示与刷新

汇总卡片展示整体情况，详细表格提供每个方法的统计、多列排序及成功率进度条。文章的 Alpine.js 示例维护指标、汇总、加载状态、筛选条件与最后更新时间，时间范围变化后刷新指标和汇总。

工程实现还应处理 HTTP 错误、业务失败和筛选参数校验，并让汇总与明细使用相同的查询条件和统计口径。

## 8. 依赖与配置

### 8.1 原始方案依赖

在已配置 Spring Boot 3.2.1 版本管理的 Maven 项目中，原文章使用：

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-aop</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

这是文章版本的依赖示例；接入其他 Spring Boot 版本时应以宿主项目的依赖管理为准。

### 8.2 原始配置示例

```yaml
server:
  port: 8080

spring:
  application:
    name: springboot-api-dashboard
  aop:
    auto: true
    proxy-target-class: true

dashboard:
  metrics:
    cleanup-interval: 300000
    max-age: 3600000
    debug-enabled: false

logging:
  level:
    com.example.dashboard: INFO
```

- `cleanup-interval`：定时清理间隔，原示例为 5 分钟。
- `max-age`：长期未更新的方法指标的清理阈值，原示例为 1 小时。
- `debug-enabled`：原资料中的调试开关，是否生效取决于工程实现。

`MetricsCleanupService` 使用 `@Scheduled` 定期触发清理，调用指标组件删除过期方法，记录当前方法数量；出现异常时记录错误日志。应用需要启用调度支持。

方法过期清理与桶历史保留是不同机制。即使桶设计支持 7 天历史，一个方法若因长期未更新被整体移除，其历史也会一同丢失。

当前仓库宿主配置使用 `devtools.apidashboard.metrics` 前缀，不能将文章中的 `dashboard.metrics` 直接当作当前模块的配置前缀。采集机制和静态页面入口也应按当前代码确认。

## 9. 快速开始与验证

### 9.1 启动原始独立示例

文章给出的独立示例项目启动命令为：

```bash
mvn clean install
mvn spring-boot:run
```

这些命令用于文章工程，不表示当前多模块仓库应在根目录直接执行相同操作。

原始示例入口：

- 面板：[http://localhost:8080/index.html](http://localhost:8080/index.html)。
- 指标：[http://localhost:8080/api/metrics](http://localhost:8080/api/metrics)。

### 9.2 演示接口

本地 README 列出的原始示例接口如下，是否存在于宿主应用中需另行确认：

| 请求 | 测试用途 |
| --- | --- |
| `GET /api/demo/hello` | 简单调用 |
| `GET /api/demo/users` | 查询用户列表 |
| `POST /api/demo/users` | 创建用户，模拟偶发失败 |
| `GET /api/demo/products?limit=10` | 查询商品列表 |
| `GET /api/demo/orders/{orderId}` | 查询订单 |
| `GET /api/demo/slow` | 模拟 2～5 秒慢调用 |
| `GET /api/demo/error` | 模拟约 50% 的失败率 |

先产生正常、慢调用和失败请求，再打开监控面板，观察计数、耗时、成功率及筛选结果。

### 9.3 API 调用

```bash
# 查询全部方法与汇总
curl "http://localhost:8080/api/metrics"
curl "http://localhost:8080/api/metrics/summary"

# 查询最近 5 分钟，过滤方法名
curl "http://localhost:8080/api/metrics/recent/5?methodFilter=user"

# 查询指定时间范围，时间戳请按需要替换
curl "http://localhost:8080/api/metrics?startTime=1640995200000&endTime=1641000000000&methodFilter=user"

# 删除操作会清除相应统计数据
curl -X DELETE "http://localhost:8080/api/metrics/stale?maxAgeMs=3600000"
curl -X DELETE "http://localhost:8080/api/metrics"
```

## 10. 应用场景与边界

- **性能定位**：筛选问题时间段，按耗时排序，寻找慢方法。
- **稳定性观察**：结合失败次数和成功率定位异常较多的接口。
- **容量评估**：识别高频调用方法，辅助判断优化或扩容方向。
- **临时排障**：在不部署大型 APM 的前提下取得基础统计。
- **趋势比较**：切换时间范围观察变化，通过方法搜索缩小分析范围。

部署和解释结果时需注意：

1. 数据只保存在当前进程内存，重启后丢失。
2. 默认不汇总多个实例的数据，各实例只反映自身调用。
3. 分级桶限制的是每个方法的历史规模；整体内存还取决于方法数量、桶对象和清理行为。
4. 粗粒度聚合具有时间边界误差，不等同于逐请求明细。
5. 同步记录、每次清理和四级更新都有开销；“适合高并发”应通过实际压测验证。
6. 原始方案没有展示分位数耗时、分布式追踪或请求正文采集，不能用平均值代替 P95/P99，也不能据此查看请求和响应内容。
7. 监控信息和删除接口应按宿主应用的访问控制策略保护。
