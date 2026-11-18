---
title: 内存对象拓扑图
subTitle: 按需生成堆快照，可视化查看对象引用关系
description: 在 Spring Boot 应用中生成 HPROF 堆快照，解析对象及引用关系，通过拓扑图、类统计和过滤功能辅助分析内存占用。
tags:
  - JVM
  - 内存分析
  - 堆快照
  - 对象拓扑
layout: layouts/docs-cn.njk
---

# 内存对象拓扑图

<!-- https://juejin.cn/post/7538050437904449582 -->

![image.png](https://p6-xtjj-sign.byteimg.com/tos-cn-i-73owjymdk6/0be7c9ba71f6423dac9db19a84d33101~tplv-73owjymdk6-jj-mark-v1:0:0:0:0:5o6Y6YeR5oqA5pyv56S-5Yy6IEAg6ZmI56u55LiD:q75.awebp?rk3s=f64ab15b&x-expires=1793847828&x-signature=Z%2FzLWiQOx%2FJ4E8BSz3tMK9r8%2Bwg%3D)


一个可嵌入任何 Spring Boot 应用的**内存对象拓扑服务**：访问 `/memviz.html` 就能在浏览器看见对象图。支持**按类/包名过滤**、**按对象大小高亮**、**点击节点看详情**。**线上可用**：默认只在你点击“生成快照”时才工作；日常零开销。


## 传统工具的痛点

- `jmap` + MAT 做离线分析：强大但**流程割裂**、**不实时**，且**换机/拷文件**麻烦，我需要一种相对**轻量的方式** ，适合“随手开网页看一眼”，能够完成一些初步判断。
- VisualVM：**不便嵌入业务**，临时接管和权限也会有顾虑。
- 线上需要：**在服务本机直接打开网页**，**快速看到对象图**，**看对象引用链**。

所以我实验性做了这个**内嵌式的内存对象拓扑图**：**点按钮 → dump → 解析 → 可视化显示**，一切在应用自己的 Web 界面里完成。


## 架构设计：为什么选“HPROF 快照 + 在线解析”

目标

- **全量对象、真实引用链**
- **无需预埋、无需重启**
- **对线上影响可控**（只在你手动触发时才消耗）

**方案**

用 `HotSpotDiagnosticMXBean` 在线触发**堆快照（HPROF）** （可选择 live/非 live）。

采用**轻量 HPROF 解析库**在应用内直接解析文件，构建**nodes/links** Graph JSON。

前端用 **纯 HTML + JS（D3 力导向图）** 渲染，支持搜索、过滤、点击查看详情。

解析库：示例使用 `org.gridkit.jvmtool:hprof-heap`，能直接读 HPROF 并遍历对象与引用，落地简单。

## 3\. 可运行代码

**项目结构**

```text
memviz/
  ├─ pom.xml
  ├─ src/main/java/com/example/memviz/
  │   ├─ MemvizApplication.java
  │   ├─ controller/MemvizController.java
  │   ├─ service/HeapDumpService.java
  │   ├─ service/HprofParseService.java
  │   ├─ model/GraphModel.java
  │   └─ util/SafeExecs.java
  └─ src/main/resources/static/
      └─ memviz.html
```
 

说明：`hprof-heap` 是一个开源的 HPROF 解析库，可以实现**遍历对象 → 找到引用关系** → 生成拓扑。

___

#### 3.2 入口 `MemvizApplication.java`

```java
package com.example.memviz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MemvizApplication {
  public static void main(String[] args) {
    SpringApplication.run(MemvizApplication.class, args);
  }
}
```

___

#### 3.3 模型 `GraphModel.java`

```java
package com.example.memviz.model;

import cn.hutool.core.util.RandomUtil;

import java.util.*;

public class GraphModel {
    public static class Node {
        public String id;        // objectId 或 class@id
        public String label;     // 类名(短)
        public String className; // 类名(全)
        public long shallowSize; // 浅表大小
        public String category;  // JDK/第三方/业务
        public int instanceCount; // 该类的实例总数
        public String formattedSize; // 格式化的大小显示
        public String packageName; // 包名
        public boolean isArray; // 是否为数组类型
        public String objectType; // 对象类型描述

        // private String bigString = new String(RandomUtil.randomBytes(1024 * 1024 * 10));

        public Node(String id, String label, String className, long shallowSize, String category) {
            this.id = id;
            this.label = label;
            this.className = className;
            this.shallowSize = shallowSize;
            this.category = category;
        }
        
        // 增强构造函数
        public Node(String id, String label, String className, long shallowSize, String category,
                   int instanceCount, String formattedSize, String packageName, boolean isArray, String objectType) {
            this.id = id;
            this.label = label;
            this.className = className;
            this.shallowSize = shallowSize;
            this.category = category;
            this.instanceCount = instanceCount;
            this.formattedSize = formattedSize;
            this.packageName = packageName;
            this.isArray = isArray;
            this.objectType = objectType;
        }
    }

    public static class Link {
        public String source;
        public String target;
        public String field;   // 通过哪个字段/元素引用

        public Link(String s, String t, String field) {
            this.source = s;
            this.target = t;
            this.field = field;
        }
    }
    
    // Top100类统计信息
    public static class TopClassStat {
        public String className;
        public String shortName;
        public String packageName;
        public String category;
        public int instanceCount; // 实例数量
        public long totalSize; // 该类所有实例的总内存（浅表大小）
        public String formattedTotalSize; // 格式化的总内存
        public long totalDeepSize; // 该类所有实例的总深度大小
        public String formattedTotalDeepSize; // 格式化的总深度大小
        public long avgSize; // 平均每个实例大小（浅表）
        public String formattedAvgSize; // 格式化的平均大小
        public long avgDeepSize; // 平均每个实例深度大小
        public String formattedAvgDeepSize; // 格式化的平均深度大小
        public int rank; // 排名
        public List<ClassInstance> topInstances; // 该类中内存占用最大的实例列表
        
        public TopClassStat(String className, String shortName, String packageName, String category,
                           int instanceCount, long totalSize, String formattedTotalSize,
                           long totalDeepSize, String formattedTotalDeepSize,
                           long avgSize, String formattedAvgSize, 
                           long avgDeepSize, String formattedAvgDeepSize,
                           int rank, List<ClassInstance> topInstances) {
            this.className = className;
            this.shortName = shortName;
            this.packageName = packageName;
            this.category = category;
            this.instanceCount = instanceCount;
            this.totalSize = totalSize;
            this.formattedTotalSize = formattedTotalSize;
            this.totalDeepSize = totalDeepSize;
            this.formattedTotalDeepSize = formattedTotalDeepSize;
            this.avgSize = avgSize;
            this.formattedAvgSize = formattedAvgSize;
            this.avgDeepSize = avgDeepSize;
            this.formattedAvgDeepSize = formattedAvgDeepSize;
            this.rank = rank;
            this.topInstances = topInstances != null ? topInstances : new ArrayList<>();
        }
    }
    
    // 类的实例信息
    public static class ClassInstance {
        public String id;
        public long size;
        public String formattedSize;
        public int rank; // 在该类中的排名
        public String packageName; // 包名
        public String objectType; // 对象类型
        public boolean isArray; // 是否数组
        public double sizePercentInClass; // 在该类中的内存占比
        
        public ClassInstance(String id, long size, String formattedSize, int rank, 
                           String packageName, String objectType, boolean isArray, double sizePercentInClass) {
            this.id = id;
            this.size = size;
            this.formattedSize = formattedSize;
            this.rank = rank;
            this.packageName = packageName;
            this.objectType = objectType;
            this.isArray = isArray;
            this.sizePercentInClass = sizePercentInClass;
        }
    }

    public List<Node> nodes = new ArrayList<>();
    public List<Link> links = new ArrayList<>();
    public List<TopClassStat> top100Classes = new ArrayList<>(); // Top100类统计列表
    public int totalObjects; // 总对象数
    public long totalMemory; // 总内存占用
    public String formattedTotalMemory; // 格式化的总内存
}
```

___

#### 3.4 触发堆快照 `HeapDumpService.java`

```java
package com.example.memviz.service;

import com.example.memviz.util.SafeExecs;
import org.springframework.stereotype.Service;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class HeapDumpService {

  private static final String HOTSPOT_BEAN = "com.sun.management:type=HotSpotDiagnostic";
  private static final String DUMP_METHOD  = "dumpHeap";
  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

  /**
   * 生成 HPROF 快照文件
   * @param live 是否仅包含存活对象（会触发一次 STW）
   * @param dir  目录（建议挂到独立磁盘/大空间）
   * @return hprof 文件路径
   */
  public File dump(boolean live, File dir) throws Exception {
    if (!dir.exists() && !dir.mkdirs()) {
      throw new IllegalStateException("Cannot create dump dir: " + dir);
    }
    String name = "heap_" + LocalDateTime.now().format(FMT) + (live ? "_live" : "") + ".hprof";
    File out = new File(dir, name);
    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    ObjectName objName = new ObjectName(HOTSPOT_BEAN);
    // 防御：限制最大文件大小（环境变量控制）
    SafeExecs.assertDiskHasSpace(dir.toPath(), 512L * 1024 * 1024); // 至少 512MB 空间

    server.invoke(objName, DUMP_METHOD, new Object[]{ out.getAbsolutePath(), live },
        new String[]{ "java.lang.String", "boolean" });
    return out;
  }
}
```

使用 `HotSpotDiagnosticMXBean.dumpHeap` 生成 HPROF 是 HotSpot 标准做法。**live=true** 时会只保留可达对象（可能出现 STW）；*
*live=false** 代价更小。Eclipse MAT
官方也推荐用该方式产出供分析。[eclipse.dev](https://link.juejin.cn/?target=https%3A%2F%2Feclipse.dev%2Fmat%2F%3Futm_source%3Dchatgpt.com "https://eclipse.dev/mat/?utm_source=chatgpt.com")

___

#### 3.5 解析 HPROF → 构图 `HprofParseService.java`
 

注：`hprof-heap` 的 API 能遍历对象实例、浅表大小、以及字段引用。对超大堆你一定要**限制 N**，并提供**过滤条件**，否则前端渲染会顶不住。



## 4\. 使用指南（线上可用的“安全姿势”）

- **默认不开销**：页面只是个静态资源；只有在你点击「生成快照」时，JVM 才会 dump。
- **限制大小**：`HprofParseService` 里 `MAX_NODES/MAX_LINKS`，避免前端卡死；用 `include` 参数过滤包前缀，目标更聚焦。
- **磁盘与权限**：把 `/tmp/memviz` 换成你线上的大磁盘目录；`SafeExecs.assertDiskHasSpace` 防炸盘。
- **鉴权**：对 `/memviz/**` 增加登录/白名单 IP 校验；生产不要裸露。
- **压测**：先在预发/灰度环境跑一遍，确认 dump 时间、解析耗时（通常几十 MB～几百 MB 的 HPROF 在几秒～十几秒级）。

___

## 5\. 实战：如何用它定位内存问题

**第一步**：线上卡顿/内存飙升 → 打开 `/memviz.html` → 生成快照。

**第二步**：加载图 → 先把 `include` 定位到你业务包，如 `com.myapp.`；观察**大节点**、**强连通**。 **第三步**：点击节点看类名 →
根据连线查看引用关系。

**第四步**：必要时扩大过滤范围或关闭“折叠集合”，看更细的对象链。

**第五步**：修复后再 dump 一次，对比图谱变化。

___

## 6\. 进阶：Live-Sampling 实时方案（给想更“炫”的你）

如果你要**更实时**的效果，可以考虑：

**JVMTI/Agent + IterateThroughHeap**：可真正遍历堆与引用，并打上对象 tag，做增量图更新。但需要 native agent 与更复杂部署。

**JFR（Java Flight Recorder）** ：低开销采集对象分配事件（非全量），在前端做**采样级**拓扑与趋势。

**混合模式**：平时跑 JFR 采样展示“热对象网络”，当有疑似泄漏时，**一键切换到 Snapshot** 做全量证据。

如果你计划把本文工具演进为“线上常驻监控”，Live-Sampling 作为常态，Snapshot 作为**取证**，是个很稳的组合。

___

## 7\. 性能 & 安全评估

**Dump 成本**：`live=true` 会触发 STW，通常在**百毫秒～数秒**（取决于堆大小/活跃度）；不紧急时优先 `live=false`。

**解析成本**：同一进程内解析 HPROF 会额外占用内存；建议**限制节点数**，或把解析放到**独立服务**（把 HPROF 传过去解析再回结果）。

**安全合规**：HPROF 含敏感对象内容；务必开启**鉴权、按需权限控制**；生成后**自动清理旧文件**（可加定时任务清理 3 天前的快照）。

**可观测性**：为 dump/parse 过程打埋点（耗时、文件大小、节点/边数量），避免工具本身成为黑盒。

___

## 8\. 常见问题（FAQ）

**Q：为什么不直接用 MAT？**  
A：MAT 非常强大（推荐用来做深度溯源），但**不嵌入**你的业务系统、链路跳转不顺手。本文方案是**轻量内嵌**，适合“随手开网页看一眼”。

**Q：HPROF 解析库为何选 GridKit？**  
A：`org.gridkit.jvmtool:hprof-heap` 轻量、API 简单，**非常适合做在线可视化的快速集成**。

**Q：能否在不 dump 的情况下拿到“所有对象”？**  
A：纯 Java 层做全堆枚举不可行；需要 **JVMTI** 层的 `IterateThroughHeap` 或类似能力（需要 agent）。这就需要 Live-Sampling
路线，相对复杂。

___

## 9\. 结语：把艰深的内存分析，变成一张图

这套方案把“重流程”的内存排查，压缩成**两步**：**生成快照 → 在线出图**。**当前实现还比较粗糙，不适合大面积进行分析，
适合局部锁定小范围定向分析，可作为基础原型DEMO参考。**

它不是取代 MAT，而是提供了一种“嵌入式、轻交互、随手查”的轻量解决方案作为一种补充手段。

[github.com/yuboon/java…](https://link.juejin.cn/?target=https%3A%2F%2Fgithub.com%2Fyuboon%2Fjava-examples%2Ftree%2Fmaster%2Fspringboot-memviz "https://github.com/yuboon/java-examples/tree/master/springboot-memviz")