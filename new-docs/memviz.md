https://juejin.cn/post/7538050437904449582

# SpringBoot 实现在线查看内存对象拓扑图 —— 给 JVM 装上“透视眼”

![image.png](https://p6-xtjj-sign.byteimg.com/tos-cn-i-73owjymdk6/0be7c9ba71f6423dac9db19a84d33101~tplv-73owjymdk6-jj-mark-v1:0:0:0:0:5o6Y6YeR5oqA5pyv56S-5Yy6IEAg6ZmI56u55LiD:q75.awebp?rk3s=f64ab15b&x-expires=1793847828&x-signature=Z%2FzLWiQOx%2FJ4E8BSz3tMK9r8%2Bwg%3D)

## 0\. 你将获得什么

一个可嵌入任何 Spring Boot 应用的**内存对象拓扑服务**：访问 `/memviz.html` 就能在浏览器看见对象图。

支持**按类/包名过滤**、**按对象大小高亮**、**点击节点看详情**。

**线上可用**：默认只在你点击“生成快照”时才工作；日常零开销。

___

## 1\. 传统工具的痛点

`jmap` + MAT 做离线分析：强大但**流程割裂**、**不实时**，且**换机/拷文件**麻烦，我需要一种相对**轻量的方式** ，适合“随手开网页看一眼”，能够完成一些初步判断。

VisualVM：**不便嵌入业务**，临时接管和权限也会有顾虑。

线上需要：**在服务本机直接打开网页**，**快速看到对象图**，**看对象引用链**。

所以我实验性做了这个**内嵌式的内存对象拓扑图**：**点按钮 → dump → 解析 → 可视化显示**，一切在应用自己的 Web 界面里完成。

___

## 2\. 架构设计：为什么选“HPROF 快照 + 在线解析”

**目标**

**1\. 全量对象、真实引用链**

**2\. 无需预埋、无需重启**

**3\. 对线上影响可控**（只在你手动触发时才消耗）

**方案**

用 `HotSpotDiagnosticMXBean` 在线触发**堆快照（HPROF）** （可选择 live/非 live）。

采用**轻量 HPROF 解析库**在应用内直接解析文件，构建**nodes/links** Graph JSON。

前端用 **纯 HTML + JS（D3 力导向图）** 渲染，支持搜索、过滤、点击查看详情。

解析库：示例使用 `org.gridkit.jvmtool:hprof-heap`，能直接读 HPROF 并遍历对象与引用，落地简单。

___

## 3\. 可运行代码

**项目结构**

```
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

```
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

```
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

```
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

```
package com.example.memviz.service;

import com.example.memviz.model.GraphModel;
import org.netbeans.lib.profiler.heap.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;

@Service
public class HprofParseService {
    
    private static final Logger log = LoggerFactory.getLogger(HprofParseService.class);

    /**
     * 安全阈值：最多加载多少对象/边进入图（避免前端崩溃）
     * 图上显示Top100类，保持完整但可读
     */
    private static final int MAX_GRAPH_NODES = 100;  // 图上显示的类数
    private static final int MAX_COLLECTION_NODES = 2000; // 收集的节点数，用于统计
    private static final int MAX_LINKS = 200;  // 增加连线数以适应更多类
    
    /**
     * 性能优化参数
     */
    private static final int BATCH_SIZE = 1000;  // 批量处理大小
    private static final int LARGE_CLASS_THRESHOLD = 10000;  // 大类阈值

    public GraphModel parseToGraph(java.io.File hprofFile,
                                   Predicate<String> classNameFilter,
                                   boolean collapseCollections) throws Exception {

        log.info("开始解析HPROF文件: {}", hprofFile.getName());

        // 检查文件大小和可用内存
        long fileSize = hprofFile.length();
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long availableMemory = maxMemory - (totalMemory - freeMemory);
        
        log.info("HPROF文件大小: {}MB, 可用内存: {}MB",
            fileSize / 1024.0 / 1024.0, availableMemory / 1024.0 / 1024.0);
        
        // 如果文件太大，警告用户并尝试优化加载
        if (fileSize > availableMemory * 0.3) {
            log.warn("检测到大型HPROF文件，启用内存优化加载模式");
            
            // 强制垃圾回收，释放更多内存
            System.gc();
            Thread.sleep(100);
            System.gc();
        }

        // Create heap from HPROF file with optimized settings
        Heap heap = null;
        try {
            heap = HeapFactory.createHeap(hprofFile);
            log.info("HPROF文件加载完成");
        } catch (OutOfMemoryError e) {
            log.error("内存不足：HPROF文件过大");
            throw new Exception("HPROF文件过大，内存不足。请增加JVM内存参数(-Xmx)或使用较小的堆转储文件", e);
        }

        try {
            return parseHeapData(heap, classNameFilter, collapseCollections);
        } finally {
            // 在finally块中确保释放资源
            if (heap != null) {
                try {
                    heap = null;
                    System.gc();
                    Thread.sleep(100);
                    System.gc();
                    log.info("已在finally块中释放HPROF文件引用");
                } catch (InterruptedException e) {
                    log.warn("释放文件引用时中断: {}", e.getMessage());
                }
            }
        }
    }

    private GraphModel parseHeapData(Heap heap, Predicate<String> classNameFilter, boolean collapseCollections) {
        
        // 1) 收集对象（可按类名过滤）- 极速优化版本，带内存监控
        List<Instance> all = new ArrayList<>(MAX_COLLECTION_NODES * 2);  // 预分配适量容量
        
        log.info("开始收集对象实例，使用激进优化策略");
        long startTime = System.currentTimeMillis();
        int processedClasses = 0;
        int skippedEmptyClasses = 0;
        int memoryCheckCounter = 0;
        
        // 使用优先队列在收集过程中就维护Top对象，避免后期排序
        PriorityQueue<Instance> topInstances = new PriorityQueue<>(
            MAX_COLLECTION_NODES * 2, Comparator.comparingLong(Instance::getSize)
        );
        
        // 直接处理，不预扫描，使用更激进的策略
        for (JavaClass javaClass : heap.getAllClasses()) {
            String className = javaClass.getName();
            
            // 更严格的早期过滤 - 临时放宽过滤条件
            if (classNameFilter != null && !classNameFilter.test(className)) {
                // 为了调试，记录被过滤掉的重要类
                if (className.contains("MemvizApplication") || className.contains("String") || className.contains("byte")) {
                    log.info("类被过滤掉: {}", className);
                }
                continue;
            }
            
            // 跳过明显的系统类和空类（基于类名）- 暂时禁用以确保不漏掉重要对象
            /*if (isLikelySystemClass(className)) {
                continue;
            }*/
            
            // 记录被处理的类
            log.debug("处理类: {}", className);
            
            // 定期检查内存使用情况
            if (++memoryCheckCounter % 100 == 0) {
                long currentFree = Runtime.getRuntime().freeMemory();
                long currentTotal = Runtime.getRuntime().totalMemory();
                long usedMemory = currentTotal - currentFree;
                double usedPercent = (double) usedMemory / Runtime.getRuntime().maxMemory() * 100;
                
                if (usedPercent > 85) {
                    log.warn("内存使用率高: {:.1f}%, 执行垃圾回收", usedPercent);
                    System.gc();
                    
                    // 重新检查
                    currentFree = Runtime.getRuntime().freeMemory();
                    currentTotal = Runtime.getRuntime().totalMemory();
                    usedMemory = currentTotal - currentFree;
                    usedPercent = (double) usedMemory / Runtime.getRuntime().maxMemory() * 100;
                    
                    if (usedPercent > 90) {
                        log.error("内存使用率危险，提前停止收集");
                        break;
                    }
                }
            }
            
            long classStart = System.currentTimeMillis();
            
            try {
                // 直接获取实例，设置超时检查
                List<Instance> instances = javaClass.getInstances();
                int instanceCount = instances.size();
                
                if (instanceCount == 0) {
                    skippedEmptyClasses++;
                    continue;
                }
                
                // 智能采样：使用优先队列自动维护Top对象
                if (instanceCount > LARGE_CLASS_THRESHOLD) {
                    // 超大类：激进采样，直接加入优先队列
                    int sampleSize = Math.min(100, instanceCount / 10); 
                    int step = Math.max(1, instanceCount / sampleSize);
                    
                    for (int i = 0; i < instanceCount; i += step) {
                        Instance inst = instances.get(i);
                        addToTopInstances(topInstances, inst, MAX_GRAPH_NODES * 2);
                    }
                    log.debug("大类采样: {}, 采样数: {}", className, Math.min(sampleSize, instanceCount));
                } else {
                    // 小类：全部加入优先队列
                    for (Instance inst : instances) {
                        addToTopInstances(topInstances, inst, MAX_COLLECTION_NODES * 2);
                    }
                }
                
                // 处理完大量数据后，帮助GC回收临时对象
                if (instanceCount > 1000) {
                    instances = null; // 显式清除引用
                }
                
                processedClasses++;
                long classEnd = System.currentTimeMillis();
                
                // 只记录耗时较长的类
                if (classEnd - classStart > 100) {
                    log.debug("耗时类: {}, 实例数: {}, 耗时: {}ms, 总计: {}", 
                        className, instanceCount, (classEnd - classStart), all.size());
                }
                
                // 每处理一定数量的类就检查是否应该停止
                if (processedClasses % 50 == 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (elapsed > 30000) { // 30秒超时
                        log.warn("处理时间过长，停止收集");
                        break;
                    }
                    log.info("进度: {}个类, {}个实例, 耗时{}ms", processedClasses, all.size(), elapsed);
                }
                
            } catch (Exception e) {
                log.warn("处理类失败: {}, 错误: {}", className, e.getMessage());
                continue;
            }
        }

        // 从优先队列中提取所有结果用于统计
        List<Instance> allCollectedInstances = new ArrayList<>(topInstances);
        allCollectedInstances.sort(Comparator.comparingLong(Instance::getSize).reversed());
        
        // 图显示用的Top10对象
        List<Instance> graphInstances = new ArrayList<>();
        int graphNodeCount = Math.min(MAX_GRAPH_NODES, allCollectedInstances.size());
        for (int i = 0; i < graphNodeCount; i++) {
            graphInstances.add(allCollectedInstances.get(i));
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        log.info("收集完成: {}个类已处理, {}个空类跳过, {}个实例收集完成(图显示{}个), 耗时{}ms", 
            processedClasses, skippedEmptyClasses, allCollectedInstances.size(), graphInstances.size(), totalTime);
        
        log.info("图节点数量: {}, 统计节点数量: {}", graphInstances.size(), allCollectedInstances.size());

        // 3) 建立 id 映射，统计类型和数量信息，生成增强数据
        Map<Long, GraphModel.Node> nodeMap = new LinkedHashMap<>();
        Map<String, Integer> classCountMap = new HashMap<>();  // 统计每个类的实例数量
        GraphModel graph = new GraphModel();

        // 用所有收集的实例进行类统计（不仅仅是图显示的Top10）
        for (Instance obj : allCollectedInstances) {
            String cn = className(heap, obj);
            classCountMap.put(cn, classCountMap.getOrDefault(cn, 0) + 1);
        }

        // 计算总内存占用 - 使用原始数据而不是过滤后的数据
        long totalMemoryBeforeFilter = 0;
        int totalObjectsBeforeFilter = 0;
        
        // 统计所有对象（用于准确的总内存计算）
        for (JavaClass javaClass : heap.getAllClasses()) {
            String className = javaClass.getName();
            
            // 应用类名过滤器进行统计
            boolean passesFilter = (classNameFilter == null || classNameFilter.test(className));
            
            // 记录重要的类信息
            if (className.contains("MemvizApplication") || className.contains("GraphModel")) {
                log.info("发现重要类: {}, 通过过滤器: {}", className, passesFilter);
            }
            
            if(!passesFilter){
                continue;
            }

            // instances 前后加耗时日志统计
            long start = System.currentTimeMillis();
            List<Instance> instances = javaClass.getInstances();
            long end = System.currentTimeMillis();
            if ((end - start) > 50) { // 只记录耗时的调用
                log.info("获取类 {} 的实例耗时: {}ms, 实例数: {}", className, (end - start), instances.size());
            }
            
            for (Instance instance : instances) {
                totalObjectsBeforeFilter++;
                totalMemoryBeforeFilter += instance.getSize();
                
                // 记录大对象
                if (instance.getSize() > 500 * 1024) { // 大于500KB的对象
                    log.info("发现大对象: 类={}, 大小={}, ID={}", className, formatSize(instance.getSize()), instance.getInstanceId());
                }
            }
        }
        
        long instanceTotalMemory = allCollectedInstances.stream().mapToLong(Instance::getSize).sum();
        graph.totalObjects = totalObjectsBeforeFilter;  // 显示总对象数，而不是过滤后的
        graph.totalMemory = totalMemoryBeforeFilter;    // 显示总内存，而不是过滤后的
        graph.formattedTotalMemory = formatSize(totalMemoryBeforeFilter);
        
        log.info("内存统计: 总对象数={}, 总内存={}", graph.totalObjects, graph.formattedTotalMemory);
        log.info("收集对象数={}, 收集内存={}, 图中对象数={}, 图中内存={}", 
            allCollectedInstances.size(), formatSize(instanceTotalMemory),
            graphInstances.size(), formatSize(graphInstances.stream().mapToLong(Instance::getSize).sum()));

        // 直接从所有类创建Top100类统计列表（不依赖收集的实例，确保统计完整）
        List<GraphModel.TopClassStat> allClassStats = new ArrayList<>();
        
        for (JavaClass javaClass : heap.getAllClasses()) {
            String className = javaClass.getName();
            
            // 应用过滤条件
            if (classNameFilter != null && !classNameFilter.test(className)) {
                continue;
            }
            
            try {
                List<Instance> instances = javaClass.getInstances();
                int instanceCount = instances.size();
                
                // 跳过没有实例的类
                if (instanceCount == 0) {
                    continue;
                }
                
                // 跳过Lambda表达式生成的匿名类
                if (className.contains("$$Lambda") || className.contains("$Lambda")) {
                    continue;
                }
                
                // 跳过其他JVM生成的内部类
                if (className.contains("$$EnhancerBySpringCGLIB$$") || 
                    className.contains("$$FastClassBySpringCGLIB$$") ||
                    className.contains("$Proxy$")) {
                    continue;
                }
                
                // 计算该类的总内存占用
                long totalSize = instances.stream().mapToLong(Instance::getSize).sum();
                long avgSize = totalSize / instanceCount;
                
                // 研究深度大小计算的可能性
                long totalDeepSize = calculateTotalDeepSize(instances);
                long avgDeepSize = totalDeepSize / instanceCount;
                
                // 记录深度大小计算结果（特别是大差异的情况）
                if (totalDeepSize > totalSize * 2) { // 深度大小是浅表大小的2倍以上
                    log.info("类 {} 深度大小显著大于浅表大小: 浅表={}({}) vs 深度={}({})", 
                        className, totalSize, formatSize(totalSize), 
                        totalDeepSize, formatSize(totalDeepSize));
                }
                
                String displayCategory = formatCategory(categoryOf(className));
                String packageName = extractPackageName(className);
                
                // 创建该类的Top实例列表（按内存大小排序，最多100个）
                List<Instance> sortedInstances = new ArrayList<>(instances);
                sortedInstances.sort(Comparator.comparingLong(Instance::getSize).reversed());
                
                List<GraphModel.ClassInstance> classInstances = new ArrayList<>();
                for (int i = 0; i < Math.min(100, sortedInstances.size()); i++) {
                    Instance inst = sortedInstances.get(i);
                    String instClassName = className(heap, inst);
                    String instPackageName = extractPackageName(instClassName);
                    String objectType = determineObjectType(instClassName);
                    boolean isArray = instClassName.contains("[");
                    
                    // 计算该实例在该类中的内存占比
                    double sizePercent = totalSize > 0 ? (double) inst.getSize() / totalSize * 100.0 : 0.0;
                    
                    GraphModel.ClassInstance classInstance = new GraphModel.ClassInstance(
                        String.valueOf(inst.getInstanceId()),
                        inst.getSize(),
                        formatSize(inst.getSize()),
                        i + 1,
                        instPackageName,
                        objectType,
                        isArray,
                        sizePercent
                    );
                    classInstances.add(classInstance);
                }
                
                GraphModel.TopClassStat stat = new GraphModel.TopClassStat(
                    className,
                    shortName(className),
                    packageName,
                    displayCategory,
                    instanceCount,
                    totalSize,
                    formatSize(totalSize),
                    totalDeepSize,  // 新增：深度大小
                    formatSize(totalDeepSize),  // 新增：格式化的深度大小
                    avgSize,
                    formatSize(avgSize),
                    avgDeepSize,  // 新增：平均深度大小
                    formatSize(avgDeepSize),  // 新增：格式化的平均深度大小
                    0,
                    classInstances
                );
                allClassStats.add(stat);
                
            } catch (Exception e) {
                log.warn("处理类{}时出错: {}", className, e.getMessage());
            }
        }
        
        // 按总内存占用排序并设置排名
        allClassStats.sort(Comparator.comparingLong((GraphModel.TopClassStat s) -> s.totalSize).reversed());
        for (int i = 0; i < Math.min(100, allClassStats.size()); i++) {
            allClassStats.get(i).rank = i + 1;
            graph.top100Classes.add(allClassStats.get(i));
        }
        
        log.info("类统计完成: 共{}个类符合过滤条件，Top100类已生成", allClassStats.size());

        // 用Top100类统计数据创建图显示用的类节点
        // 按总内存大小排序，取Top100用于图显示
        List<GraphModel.TopClassStat> topClassesForGraph = new ArrayList<>(allClassStats);
        topClassesForGraph.sort(Comparator.comparingLong((GraphModel.TopClassStat s) -> s.totalSize).reversed());
        
        // 为图显示的Top100类创建节点
        int graphClassCount = Math.min(MAX_GRAPH_NODES, topClassesForGraph.size());
        for (int i = 0; i < graphClassCount; i++) {
            GraphModel.TopClassStat classStat = topClassesForGraph.get(i);
            String cn = classStat.className;
            
            // 创建类级别的节点，显示类的聚合信息
            String enhancedLabel = String.format("%s (%d个实例, %s, %s)", 
                classStat.shortName, classStat.instanceCount, classStat.formattedTotalSize, classStat.category);
            
            GraphModel.Node n = new GraphModel.Node(
                    "class_" + cn.hashCode(), // 使用类名hash作为节点ID
                    enhancedLabel,
                    cn,
                    classStat.totalSize,
                    classStat.category,
                    classStat.instanceCount,
                    classStat.formattedTotalSize,
                    classStat.packageName,
                    cn.contains("["),
                    determineObjectType(cn));
            
            nodeMap.put((long)cn.hashCode(), n); // 用类名hash作为key
            graph.nodes.add(n);
        }

        // 4) 建立类级别的引用边（基于堆中真实的对象引用关系）
        log.info("开始建立类级别引用边，图类数: {}", graphClassCount);
        int linkCount = 0;
        int potentialLinks = 0;
        
        // 分析类之间的引用关系 - 只基于堆中真实的对象引用
        Map<String, Set<String>> classReferences = new HashMap<>();
        
        for (Instance obj : allCollectedInstances) {
            String sourceClassName = className(heap, obj);
            
            for (FieldValue fieldValue : obj.getFieldValues()) {
                potentialLinks++;
                if (fieldValue instanceof ObjectFieldValue) {
                    ObjectFieldValue objFieldValue = (ObjectFieldValue) fieldValue;
                    Instance target = objFieldValue.getInstance();
                    if (target != null) {
                        String targetClassName = className(heap, target);
                        
                        // 避免自引用，也避免Lambda和代理类的连线
                        if (!sourceClassName.equals(targetClassName) && 
                            !isGeneratedClass(targetClassName) && 
                            !isGeneratedClass(sourceClassName)) {
                            classReferences.computeIfAbsent(sourceClassName, k -> new HashSet<>())
                                          .add(targetClassName);
                        }
                    }
                }
            }
        }
        
        log.info("检测到类引用关系: {}", classReferences.size());
        
        // 为图中显示的类创建连线
        for (int i = 0; i < graphClassCount && linkCount < MAX_LINKS; i++) {
            String sourceClass = topClassesForGraph.get(i).className;
            Set<String> targets = classReferences.get(sourceClass);
            
            if (targets != null) {
                for (String targetClass : targets) {
                    // 检查目标类是否也在图显示范围内
                    boolean targetInGraph = topClassesForGraph.stream()
                        .limit(graphClassCount)
                        .anyMatch(stat -> stat.className.equals(targetClass));
                    
                    if (targetInGraph) {
                        String sourceId = "class_" + sourceClass.hashCode();
                        String targetId = "class_" + targetClass.hashCode();
                        
                        // 添加更详细的连线信息
                        String linkLabel = "引用";
                        graph.links.add(new GraphModel.Link(sourceId, targetId, linkLabel));
                        linkCount++;
                        
                        if (linkCount >= MAX_LINKS) {
                            log.info("达到最大连线数限制: {}", MAX_LINKS);
                            break;
                        }
                    }
                }
            }
        }
        
        log.info("连线建立完成: 处理了{}个潜在连线，实际创建{}个连线", potentialLinks, linkCount);

        // 5) 可选：把大型集合折叠为"聚合节点"，减少噪音
        if (collapseCollections) {
            log.info("开始折叠集合类型节点");
            collapseCollectionLikeNodes(graph);
        }

        log.info("图构建完成: {}个节点, {}个链接", graph.nodes.size(), graph.links.size());

        return graph;
    }

    private static String className(Heap heap, Instance instance) {
        return instance.getJavaClass().getName();
    }

    private static String shortName(String fqcn) {
        int p = fqcn.lastIndexOf('.');
        return p >= 0 ? fqcn.substring(p + 1) : fqcn;
    }

    private static String categoryOf(String fqcn) {
        if (fqcn.startsWith("java.") || fqcn.startsWith("javax.") || fqcn.startsWith("jdk.")) return "JDK";
        if (fqcn.startsWith("org.") || fqcn.startsWith("com.")) return "3rd";
        return "app";
    }

    /**
     * 格式化字节大小，让显示更直观
     */
    private static String formatSize(long sizeInBytes) {
        if (sizeInBytes < 1024) {
            return sizeInBytes + "B";
        } else if (sizeInBytes < 1024 * 1024) {
            return String.format("%.1fKB", sizeInBytes / 1024.0);
        } else if (sizeInBytes < 1024 * 1024 * 1024) {
            return String.format("%.2fMB", sizeInBytes / (1024.0 * 1024));
        } else {
            return String.format("%.2fGB", sizeInBytes / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * 格式化类别名称，让显示更直观
     */
    private static String formatCategory(String category) {
        switch (category) {
            case "JDK":
                return "JDK类";
            case "3rd":
                return "第三方";
            case "app":
                return "业务代码";
            default:
                return "未知";
        }
    }

    /**
     * 提取包名
     */
    private static String extractPackageName(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            return className.substring(0, lastDot);
        }
        return "默认包";
    }

    /**
     * 确定对象类型
     */
    private static String determineObjectType(String className) {
        if (className.contains("[")) {
            return "数组";
        } else if (className.contains("$")) {
            if (className.contains("Lambda")) {
                return "Lambda表达式";
            } else {
                return "内部类";
            }
        } else if (className.startsWith("java.util.") && 
                  (className.contains("List") || className.contains("Set") || className.contains("Map"))) {
            return "集合类";
        } else if (className.startsWith("java.lang.")) {
            return "基础类型";
        } else {
            return "普通类";
        }
    }

    /**
     * 向优先队列添加实例，自动维护Top-N
     */
    private void addToTopInstances(PriorityQueue<Instance> topInstances, Instance instance, int maxSize) {
        if (topInstances.size() < maxSize) {
            topInstances.offer(instance);
        } else if (instance.getSize() > topInstances.peek().getSize()) {
            topInstances.poll();
            topInstances.offer(instance);
        }
    }

    /**
     * 快速选择Top-N最大的对象，避免全排序的性能问题
     */
    private List<Instance> quickSelectTopN(List<Instance> instances, int n) {
        if (instances.size() <= n) {
            return instances;
        }
        
        // 使用优先队列（小顶堆）来维护Top-N
        PriorityQueue<Instance> topN = new PriorityQueue<>(
            Comparator.comparingLong(Instance::getSize)
        );
        
        int processed = 0;
        for (Instance instance : instances) {
            if (topN.size() < n) {
                topN.offer(instance);
            } else if (instance.getSize() > topN.peek().getSize()) {
                topN.poll();
                topN.offer(instance);
            }
            
            // 每处理10000个对象记录一次进度
            if (++processed % 10000 == 0) {
                log.debug("快速选择进度: {}/{}", processed, instances.size());
            }
        }
        
        // 将结果转换为List并按大小降序排序
        List<Instance> result = new ArrayList<>(topN);
        result.sort(Comparator.comparingLong(Instance::getSize).reversed());
        
        log.info("快速选择完成，从{}个对象中选出{}个最大对象", instances.size(), result.size());
        return result;
    }
    private static boolean isLikelySystemClass(String className) {
        // 跳过一些已知很慢或不重要的类
        return className.startsWith("java.lang.Class") ||
               className.startsWith("java.lang.String") ||
               className.startsWith("java.lang.Object[]") ||
               className.startsWith("java.util.concurrent") ||
               className.contains("$$Lambda") ||
               className.contains("$Proxy") ||
               className.startsWith("sun.") ||
               className.startsWith("jdk.internal.") ||
               className.endsWith("[][]") || // 多维数组通常很慢
               className.contains("reflect.Method") ||
               className.contains("reflect.Field");
        //return false;
    }

    /**
     * 集合折叠策略：将集合类型的多个元素聚合显示
     */
    private void collapseCollectionLikeNodes(GraphModel graph) {
        Map<String, Integer> collectionElementCount = new HashMap<>();
        Set<String> collectionNodeIds = new HashSet<>();
        Set<GraphModel.Link> linksToRemove = new HashSet<>();
        Map<String, GraphModel.Link> collectionLinks = new HashMap<>();
        
        // 1. 识别集合类型的节点
        for (GraphModel.Node node : graph.nodes) {
            if (isCollectionType(node.className)) {
                collectionNodeIds.add(node.id);
            }
        }
        
        // 2. 统计每个集合的元素数量，并准备聚合连线
        for (GraphModel.Link link : graph.links) {
            if (collectionNodeIds.contains(link.source)) {
                // 这是从集合指向元素的连线
                String collectionId = link.source;
                collectionElementCount.put(collectionId, 
                    collectionElementCount.getOrDefault(collectionId, 0) + 1);
                linksToRemove.add(link);
                
                // 保留一条代表性连线，用于显示聚合信息
                String key = collectionId + "->elements";
                if (!collectionLinks.containsKey(key)) {
                    GraphModel.Node sourceNode = graph.nodes.stream()
                        .filter(n -> n.id.equals(collectionId))
                        .findFirst().orElse(null);
                    if (sourceNode != null) {
                        collectionLinks.put(key, new GraphModel.Link(
                            collectionId, 
                            "collapsed_" + collectionId, 
                            collectionElementCount.get(collectionId) + "个元素"
                        ));
                    }
                }
            }
        }
        
        // 3. 移除原始的集合元素连线
        graph.links.removeAll(linksToRemove);
        
        // 4. 更新集合节点的显示信息
        for (GraphModel.Node node : graph.nodes) {
            if (collectionNodeIds.contains(node.id)) {
                int elementCount = collectionElementCount.getOrDefault(node.id, 0);
                if (elementCount > 0) {
                    // 更新节点标签，显示元素数量
                    String originalLabel = node.label;
                    node.label = String.format("%s [%d个元素]", 
                        originalLabel.split("\(")[0].trim(), elementCount);
                    
                    // 添加聚合信息到对象类型
                    node.objectType = node.objectType + " (已折叠)";
                }
            }
        }
        
        // 5. 移除被折叠的元素节点（可选，这里保留以维持图的完整性）
        // 实际应用中可以选择性移除孤立的元素节点
        
        log.info("集合折叠完成: {}个集合被处理", collectionElementCount.size());
    }
    
    /**
     * 计算一组实例的总深度大小
     */
    private long calculateTotalDeepSize(List<Instance> instances) {
        long totalDeepSize = 0;
        Set<Long> globalVisited = new HashSet<>(); // 全局访问记录，避免重复计算共享对象
        
        for (Instance instance : instances) {
            totalDeepSize += calculateDeepSize(instance, globalVisited, 0, 5); // 最大递归深度5
        }
        
        return totalDeepSize;
    }
    
    /**
     * 递归计算单个对象的深度大小
     * @param obj 要计算的对象
     * @param visited 已访问的对象ID集合，防止循环引用
     * @param depth 当前递归深度
     * @param maxDepth 最大递归深度限制
     * @return 深度大小（包含所有引用对象）
     */
    private long calculateDeepSize(Instance obj, Set<Long> visited, int depth, int maxDepth) {
        if (obj == null || depth >= maxDepth) {
            return 0;
        }
        
        long objId = obj.getInstanceId();
        if (visited.contains(objId)) {
            return 0; // 已经计算过，避免重复
        }
        
        visited.add(objId);
        long totalSize = obj.getSize(); // 从浅表大小开始
        
        try {
            // 遍历所有对象字段
            for (FieldValue fieldValue : obj.getFieldValues()) {
                if (fieldValue instanceof ObjectFieldValue) {
                    ObjectFieldValue objFieldValue = (ObjectFieldValue) fieldValue;
                    Instance referencedObj = objFieldValue.getInstance();
                    
                    if (referencedObj != null) {
                        // 递归计算引用对象的大小
                        totalSize += calculateDeepSize(referencedObj, visited, depth + 1, maxDepth);
                    }
                }
            }
        } catch (Exception e) {
            // 如果访问字段失败，记录日志但继续
            log.debug("计算深度大小时访问对象字段失败: {}, 对象类型: {}", 
                e.getMessage(), obj.getJavaClass().getName());
        }
        
        return totalSize;
    }
    
    /**
     * 判断是否为JVM生成的类（Lambda、CGLIB代理等）
     */
    private static boolean isGeneratedClass(String className) {
        return className.contains("$$Lambda") || 
               className.contains("$Lambda") ||
               className.contains("$$EnhancerBySpringCGLIB$$") ||
               className.contains("$$FastClassBySpringCGLIB$$") ||
               className.contains("$Proxy$") ||
               className.contains("$$SpringCGLIB$$");
    }
    
    /**
     * 判断是否为集合类型
     */
    private boolean isCollectionType(String className) {
        return className.contains("ArrayList") || 
               className.contains("LinkedList") ||
               className.contains("HashMap") ||
               className.contains("LinkedHashMap") ||
               className.contains("TreeMap") ||
               className.contains("HashSet") ||
               className.contains("LinkedHashSet") ||
               className.contains("TreeSet") ||
               className.contains("Vector") ||
               className.contains("Stack") ||
               className.contains("ConcurrentHashMap");
    }
}
```

注：`hprof-heap` 的 API 能遍历对象实例、浅表大小、以及字段引用。对超大堆你一定要**限制 N**，并提供**过滤条件**，否则前端渲染会顶不住。

___

#### 3.6 控制器 `MemvizController.java`

```
package com.example.memviz.controller;

import com.example.memviz.model.GraphModel;
import com.example.memviz.service.HeapDumpService;
import com.example.memviz.service.HprofParseService;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;

@RestController
@RequestMapping("/memviz")
public class MemvizController {

  private final HeapDumpService dumpService;
  private final HprofParseService parseService;

  public MemvizController(HeapDumpService dumpService, HprofParseService parseService) {
    this.dumpService = dumpService;
    this.parseService = parseService;
  }

  /** 触发一次快照，返回文件名（安全：默认 live=false） */
  @PostMapping("/snapshot")
  public Map<String, String> snapshot(@RequestParam(defaultValue = "false") boolean live,
                                      @RequestParam(defaultValue = "/tmp/memviz") String dir) throws Exception {
    File f = dumpService.dump(live, new File(dir));
    return Map.of("file", f.getAbsolutePath());
  }

  /** 解析指定快照 → 图模型（支持过滤&折叠） */
  @GetMapping(value = "/graph", produces = MediaType.APPLICATION_JSON_VALUE)
  public GraphModel graph(@RequestParam String file,
                          @RequestParam(required = false) String include, // 例如: com.myapp.,java.util.HashMap
                          @RequestParam(defaultValue = "true") boolean collapseCollections) throws Exception {

    Predicate<String> filter = null;
    if (StringUtils.hasText(include)) {
      String[] prefixes = include.split(",");
      filter = fqcn -> {
        for (String p : prefixes) if (fqcn.startsWith(p.trim())) return true;
        return false;
      };
    }
    return parseService.parseToGraph(new File(file), filter, collapseCollections);
  }
}
```

___

#### 3.7 防御工具 `SafeExecs.java`

```
package com.example.memviz.util;

import java.io.IOException;
import java.nio.file.*;

public class SafeExecs {
  public static void assertDiskHasSpace(Path dir, long minFreeBytes) throws IOException {
    FileStore store = Files.getFileStore(dir);
    if (store.getUsableSpace() < minFreeBytes) {
      throw new IllegalStateException("Low disk space for heap dump: need " + minFreeBytes + " bytes free");
    }
  }
}
```

___

## 4\. 使用指南（线上可用的“安全姿势”）

**1\. 默认不开销**：页面只是个静态资源；只有在你点击「生成快照」时，JVM 才会 dump。

**2\. 限制大小**：`HprofParseService` 里 `MAX_NODES/MAX_LINKS`，避免前端卡死；用 `include` 参数过滤包前缀，目标更聚焦。

**3\. 磁盘与权限**：把 `/tmp/memviz` 换成你线上的大磁盘目录；`SafeExecs.assertDiskHasSpace` 防炸盘。

**4\. 鉴权**：对 `/memviz/**` 增加登录/白名单 IP 校验；生产不要裸露。

**5\. 压测**：先在预发/灰度环境跑一遍，确认 dump 时间、解析耗时（通常几十 MB～几百 MB 的 HPROF 在几秒～十几秒级）。

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