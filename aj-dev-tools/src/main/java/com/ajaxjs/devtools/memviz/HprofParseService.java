package com.ajaxjs.devtools.memviz;

import com.ajaxjs.devtools.memviz.model.*;
import com.ajaxjs.devtools.sysmonitor.Utils;
import lombok.extern.slf4j.Slf4j;
import org.netbeans.lib.profiler.heap.*;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

/**
 * HPROF 堆转储解析服务，将对象、类统计和抽样引用关系转换为前端图模型。
 * <p>图节点按类聚合，以保留大小的汇总值排序；引用边来自候选对象的字段，
 * 因此结果用于定位较大的类和观察部分引用关系，不代表完整的对象可达图。
 * 保留大小按实例累加时可能存在重叠，不等于去重后的实际堆占用。</p>
 * <p>解析和统计为同步操作，可能消耗较多内存与时间。</p>
 */
@Slf4j
@Service
public class HprofParseService {
    /**
     * 最终类节点及类排行榜的数量上限，同时用于部分候选对象采样。
     */
    private static final int MAX_GRAPH_NODES = 100;

    /**
     * 常规类候选对象队列的容量基数；调用处使用其两倍作为容量参数。
     */
    private static final int MAX_COLLECTION_NODES = 2000;

    /**
     * 最终图保留的类间引用边数量上限。
     */
    private static final int MAX_LINKS = 200;

    /**
     * 预留的批次大小常量；当前解析流程未使用。
     */
    private static final int BATCH_SIZE = 1000;

    /**
     * 大类采样阈值；实例数超过此值时，候选收集阶段按步长抽样。
     */
    private static final int LARGE_CLASS_THRESHOLD = 10000;

    /**
     * 读取 HPROF 文件并同步构建内存图。
     * <p>文件较大时会请求 GC；结束时再次请求 GC 以帮助回收解析对象，
     * 但不保证立即释放所有内存。I/O 失败和内存不足被包装为运行时异常。</p>
     *
     * @param hprofFile           待解析的本地堆转储文件，不应为 null
     * @param classNameFilter     类名筛选条件；null 表示不限制类名
     * @param collapseCollections 是否执行集合节点的展示简化
     * @return 解析出的类聚合图及统计
     * @throws RuntimeException HPROF 读取失败或解析时内存不足
     */
    public GraphModel parseToGraph(File hprofFile, Predicate<String> classNameFilter, boolean collapseCollections) {
        log.info("开始解析HPROF文件: {}", hprofFile.getName());

        Runtime runtime = Runtime.getRuntime();
        long fileSize = hprofFile.length();// 检查文件大小和可用内存
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long availableMemory = maxMemory - (totalMemory - freeMemory);

        log.info("HPROF文件大小: {}MB, 可用内存: {}MB", fileSize / 1024.0 / 1024.0, availableMemory / 1024.0 / 1024.0);

        if (fileSize > availableMemory * 0.3) {// 如果文件太大，警告用户并尝试优化加载
            log.warn("检测到大型HPROF文件，启用内存优化加载模式");
            System.gc();  // 强制垃圾回收，释放更多内存
            Utils.sleep(0.1f);
            System.gc();
        }

        Heap heap;// Create heap from HPROF file with optimized settings

        try {
            heap = HeapFactory.createHeap(hprofFile);
            log.info("HPROF文件加载完成");
        } catch (OutOfMemoryError | IOException e) {
            log.error("内存不足：HPROF文件过大");
            throw new RuntimeException("HPROF文件过大，内存不足。请增加JVM内存参数(-Xmx)或使用较小的堆转储文件", e);
        }

        try {
            return parseHeapData(heap, classNameFilter, collapseCollections);
        } finally {
            try {  // 在finally块中确保释放资源
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

    /**
     * 扫描堆类与实例，生成全局统计、类排行榜和抽样引用边。
     * <p>候选对象按浅表大小筛选，大类按步长采样；候选阶段设有内存与耗时检查，
     * 这些检查不构成整个解析过程的硬性超时。全局统计和类统计仍会再次遍历匹配类。
     * 引用边只来自候选对象的引用字段，不覆盖所有实例和数组元素。</p>
     *
     * @param heap                解析库创建的堆视图
     * @param classNameFilter     类名筛选器，null 表示不过滤
     * @param collapseCollections 是否简化集合节点展示
     * @return 包含统计及有限引用关系的图模型
     */
    private GraphModel parseHeapData(Heap heap, Predicate<String> classNameFilter, boolean collapseCollections) {
        // 1) 收集对象（可按类名过滤）- 极速优化版本，带内存监控
        List<Instance> all = new ArrayList<>(MAX_COLLECTION_NODES * 2);  // 预分配适量容量

        log.info("开始收集对象实例，使用激进优化策略");
        long startTime = System.currentTimeMillis();
        int processedClasses = 0;
        int skippedEmptyClasses = 0;
        int memoryCheckCounter = 0;

        // 使用优先队列在收集过程中就维护Top对象，避免后期排序
        PriorityQueue<Instance> topInstances = new PriorityQueue<>(MAX_COLLECTION_NODES * 2, Comparator.comparingLong(Instance::getSize));

        for (JavaClass javaClass : heap.getAllClasses()) { // 直接处理，不预扫描，使用更激进的策略
            String className = javaClass.getName();

            if (classNameFilter != null && !classNameFilter.test(className)) { // 更严格的早期过滤 - 临时放宽过滤条件
                // 为了调试，记录被过滤掉的重要类
                if (className.contains("MemvizApplication") || className.contains("String") || className.contains("byte"))
                    log.info("类被过滤掉: {}", className);

                continue;
            }

            // 跳过明显的系统类和空类（基于类名）- 暂时禁用以确保不漏掉重要对象
            /*if (isLikelySystemClass(className)) {
                continue;
            }*/

            log.debug("处理类: {}", className);  // 记录被处理的类

            // 定期检查内存使用情况
            if (++memoryCheckCounter % 100 == 0) {
                long currentFree = Runtime.getRuntime().freeMemory();
                long currentTotal = Runtime.getRuntime().totalMemory();
                long usedMemory = currentTotal - currentFree;
                double usedPercent = (double) usedMemory / Runtime.getRuntime().maxMemory() * 100;

                if (usedPercent > 85) {
                    log.warn("内存使用率高: {:.1f}%, 执行垃圾回收", usedPercent);
                    System.gc();
                    currentFree = Runtime.getRuntime().freeMemory();     // 重新检查
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
                List<Instance> instances = javaClass.getInstances(); // 直接获取实例，设置超时检查
                int instanceCount = instances.size();

                if (instanceCount == 0) {
                    skippedEmptyClasses++;
                    continue;
                }

                // 智能采样：使用优先队列自动维护Top对象
                if (instanceCount > LARGE_CLASS_THRESHOLD) {
                    int sampleSize = Math.min(100, instanceCount / 10);// 超大类：激进采样，直接加入优先队列
                    int step = Math.max(1, instanceCount / sampleSize);

                    for (int i = 0; i < instanceCount; i += step) {
                        Instance inst = instances.get(i);
                        addToTopInstances(topInstances, inst, MAX_GRAPH_NODES * 2);
                    }

                    log.debug("大类采样: {}, 采样数: {}", className, sampleSize);
                } else {
                    for (Instance inst : instances)  // 小类：全部加入优先队列
                        addToTopInstances(topInstances, inst, MAX_COLLECTION_NODES * 2);
                }

                if (instanceCount > 1000)// 处理完大量数据后，帮助GC回收临时对象
                    instances = null; // 显式清除引用

                processedClasses++;
                long classEnd = System.currentTimeMillis();

                if (classEnd - classStart > 100) // 只记录耗时较长的类
                    log.debug("耗时类: {}, 实例数: {}, 耗时: {}ms, 总计: {}", className, instanceCount, (classEnd - classStart), all.size());

                if (processedClasses % 50 == 0) {// 每处理一定数量的类就检查是否应该停止
                    long elapsed = System.currentTimeMillis() - startTime;

                    if (elapsed > 30000) { // 30秒超时
                        log.warn("处理时间过长，停止收集");
                        break;
                    }

                    log.info("进度: {}个类, {}个实例, 耗时{}ms", processedClasses, all.size(), elapsed);
                }

            } catch (Exception e) {
                log.warn("处理类失败: {}, 错误: {}", className, e.getMessage());
            }
        }

        // 从优先队列中提取所有结果用于统计
        List<Instance> allCollectedInstances = new ArrayList<>(topInstances);
        allCollectedInstances.sort(Comparator.comparingLong(Instance::getSize).reversed());

        // 图显示用的Top100对象
        List<Instance> graphInstances = new ArrayList<>();
        int graphNodeCount = Math.min(MAX_GRAPH_NODES, allCollectedInstances.size());
        for (int i = 0; i < graphNodeCount; i++)
            graphInstances.add(allCollectedInstances.get(i));

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("收集完成: {}个类已处理, {}个空类跳过, {}个实例收集完成(图显示{}个), 耗时{}ms",
                processedClasses, skippedEmptyClasses, allCollectedInstances.size(), graphInstances.size(), totalTime);

        log.info("图节点数量: {}, 统计节点数量: {}", graphInstances.size(), allCollectedInstances.size());

        // 3) 建立 id 映射，统计类型和数量信息，生成增强数据
        Map<Long, Node> nodeMap = new LinkedHashMap<>();
        Map<String, Integer> classCountMap = new HashMap<>();  // 统计每个类的实例数量
        GraphModel graph = new GraphModel();

        // 用所有收集的实例进行类统计（不仅仅是图显示的Top10）
        for (Instance obj : allCollectedInstances) {
            String cn = className(heap, obj);
            classCountMap.put(cn, classCountMap.getOrDefault(cn, 0) + 1);
        }

        long totalMemoryBeforeFilter = 0;// 计算总内存占用 - 使用原始数据而不是过滤后的数据
        int totalObjectsBeforeFilter = 0;

        for (JavaClass javaClass : heap.getAllClasses()) {// 统计所有对象（用于准确的总内存计算）
            String className = javaClass.getName();
            boolean passesFilter = (classNameFilter == null || classNameFilter.test(className)); // 应用类名过滤器进行统计

            // 记录重要的类信息
            if (className.contains("MemvizApplication") || className.contains("GraphModel"))
                log.info("发现重要类: {}, 通过过滤器: {}", className, passesFilter);

            if (!passesFilter)
                continue;

            // instances 前后加耗时日志统计
            long start = System.currentTimeMillis();
            List<Instance> instances = javaClass.getInstances();
            long end = System.currentTimeMillis();

            if ((end - start) > 50) // 只记录耗时的调用
                log.info("获取类 {} 的实例耗时: {}ms, 实例数: {}", className, (end - start), instances.size());

            for (Instance instance : instances) {
                totalObjectsBeforeFilter++;
                totalMemoryBeforeFilter += instance.getSize();

                // 记录大对象
                if (instance.getSize() > 500 * 1024)  // 大于500KB的对象
                    log.info("发现大对象: 类={}, 大小={}, ID={}", className, formatSize(instance.getSize()), instance.getInstanceId());
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
        List<TopClassStat> allClassStats = new ArrayList<>();

        for (JavaClass javaClass : heap.getAllClasses()) {
            String className = javaClass.getName();

            // 应用过滤条件
            if (classNameFilter != null && !classNameFilter.test(className))
                continue;

            try {
                List<Instance> instances = javaClass.getInstances();
                int instanceCount = instances.size();

                if (instanceCount == 0)  // 跳过没有实例的类
                    continue;


                // 跳过Lambda表达式生成的匿名类
                if (className.contains("$$Lambda") || className.contains("$Lambda"))
                    continue;

                // 跳过其他JVM生成的内部类
                if (className.contains("$$EnhancerBySpringCGLIB$$") ||
                        className.contains("$$FastClassBySpringCGLIB$$") ||
                        className.contains("$Proxy$"))
                    continue;

                // 删除测试代码，使用正式的API调用

                // 计算该类的总内存占用（浅表大小）
                long totalSize = instances.stream().mapToLong(Instance::getSize).sum();
                long avgSize = totalSize / instanceCount;

                // 使用NetBeans API计算深度大小（保留大小）
                long totalRetainedSize = calculateTotalRetainedSize(instances);
                long avgRetainedSize = totalRetainedSize / instanceCount;

                // 记录保留大小计算结果（特别是大差异的情况）
                if (totalRetainedSize > totalSize * 2) // 保留大小是浅表大小的2倍以上
                    log.info("类 {} 保留大小显著大于浅表大小: 浅表={}({}) vs 保留={}({})",
                            className, totalSize, formatSize(totalSize),
                            totalRetainedSize, formatSize(totalRetainedSize));

                String displayCategory = formatCategory(categoryOf(className));
                String packageName = extractPackageName(className);

                // 创建该类的Top实例列表（按深度大小排序，最多100个）
                List<Instance> sortedInstances = new ArrayList<>(instances);
                // 按深度大小（保留大小）排序，如果获取失败则使用浅表大小
                sortedInstances.sort((a, b) -> {
                    try {
                        long retainedA = a.getRetainedSize();
                        long retainedB = b.getRetainedSize();
                        // 确保深度大小至少等于浅表大小
                        retainedA = Math.max(retainedA, a.getSize());
                        retainedB = Math.max(retainedB, b.getSize())
                        ;
                        return Long.compare(retainedB, retainedA); // 降序
                    } catch (Exception e) {
                        // 如果获取深度大小失败，回退到浅表大小比较
                        return Long.compare(b.getSize(), a.getSize());
                    }
                });

                List<ClassInstance> classInstances = new ArrayList<>();
                for (int i = 0; i < Math.min(100, sortedInstances.size()); i++) {
                    Instance inst = sortedInstances.get(i);
                    String instClassName = className(heap, inst);
                    String instPackageName = extractPackageName(instClassName);
                    String objectType = determineObjectType(instClassName);
                    boolean isArray = instClassName.contains("[");

                    // 获取实例的深度大小
                    long instanceRetainedSize;
                    try {
                        instanceRetainedSize = inst.getRetainedSize();
                        // 确保深度大小至少等于浅表大小
                        instanceRetainedSize = Math.max(instanceRetainedSize, inst.getSize());
                    } catch (Exception e) {
                        log.debug("获取实例深度大小失败，使用浅表大小: {}", e.getMessage());
                        instanceRetainedSize = inst.getSize();
                    }

                    // 计算该实例在该类中的内存占比（基于深度大小）
                    double sizePercent = totalRetainedSize > 0 ? (double) instanceRetainedSize / totalRetainedSize * 100.0 : 0.0;

                    ClassInstance classInstance = new ClassInstance(
                            String.valueOf(inst.getInstanceId()),
                            inst.getSize(),
                            formatSize(inst.getSize()),
                            instanceRetainedSize,  // 添加深度大小
                            formatSize(instanceRetainedSize),  // 格式化的深度大小
                            i + 1,
                            instPackageName,
                            objectType,
                            isArray,
                            sizePercent
                    );

                    // 添加对象属性分析
                    analyzeInstanceFields(inst, classInstance);

                    // 注意：不再为每个实例创建单独的节点，而是在类级别聚合显示
                    // 实例详细信息保存在ClassInstance中，通过Top100类统计展示

                    classInstances.add(classInstance);
                }

                TopClassStat stat = new TopClassStat(
                        className,
                        shortName(className),
                        packageName,
                        displayCategory,
                        instanceCount,
                        totalSize,
                        formatSize(totalSize),
                        totalRetainedSize,  // 使用保留大小
                        formatSize(totalRetainedSize),  // 格式化的保留大小
                        avgSize,
                        formatSize(avgSize),
                        avgRetainedSize,  // 平均保留大小
                        formatSize(avgRetainedSize),  // 格式化的平均保留大小
                        0,
                        classInstances
                );
                allClassStats.add(stat);

            } catch (Exception e) {
                log.warn("处理类{}时出错: {}", className, e.getMessage());
            }
        }

        // 按总深度大小排序并设置排名
        allClassStats.sort(Comparator.comparingLong((TopClassStat s) -> s.totalDeepSize).reversed());
        for (int i = 0; i < Math.min(100, allClassStats.size()); i++) {
            allClassStats.get(i).rank = i + 1;
            graph.top100Classes.add(allClassStats.get(i));
        }

        log.info("类统计完成: 共{}个类符合过滤条件，Top100类已生成", allClassStats.size());

        // 用Top100类统计数据创建图显示用的类节点
        // 按总深度大小排序，取Top100用于图显示
        List<TopClassStat> topClassesForGraph = new ArrayList<>(allClassStats);
        topClassesForGraph.sort(Comparator.comparingLong((TopClassStat s) -> s.totalDeepSize).reversed());

        // 为图显示的Top100类创建节点
        int graphClassCount = Math.min(MAX_GRAPH_NODES, topClassesForGraph.size());

        for (int i = 0; i < graphClassCount; i++) {
            TopClassStat classStat = topClassesForGraph.get(i);
            String cn = classStat.className;

            // 创建类级别的节点，显示类的聚合信息（包含深度大小）
            String enhancedLabel = String.format("%s (%d个实例, 浅表:%s, 深度:%s, %s)",
                    classStat.shortName, classStat.instanceCount,
                    classStat.formattedTotalSize, classStat.formattedTotalDeepSize, classStat.category);

            Node n = new Node(
                    "class_" + cn.hashCode(), // 使用类名hash作为节点ID
                    enhancedLabel,
                    cn,
                    classStat.totalSize,
                    classStat.category,
                    classStat.instanceCount,
                    classStat.formattedTotalSize,
                    classStat.packageName,
                    cn.contains("["),
                    determineObjectType(cn),
                    classStat.totalDeepSize,         // 深度大小
                    classStat.formattedTotalDeepSize // 格式化的深度大小
            );

            nodeMap.put((long) cn.hashCode(), n); // 用类名hash作为key
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
                                !isGeneratedClass(sourceClassName))
                            classReferences.computeIfAbsent(sourceClassName, k -> new HashSet<>())
                                    .add(targetClassName);
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
                        graph.links.add(new Link(sourceId, targetId, linkLabel));
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

    /**
     * 读取实例所属类的完整名称。heap 参数当前保留但不参与计算。
     *
     * @param heap     解析库创建的堆视图
     * @param instance 待分析的堆实例
     * @return 解析库提供的类名
     */
    private static String className(Heap heap, Instance instance) {
        return instance.getJavaClass().getName();
    }

    /**
     * 截取最后一个点号之后的名称；不对数组描述符做额外规范化。
     *
     * @param fqcn 非 null 的完整类名
     * @return 简短类名
     */
    private static String shortName(String fqcn) {
        int p = fqcn.lastIndexOf('.');

        return p >= 0 ? fqcn.substring(p + 1) : fqcn;
    }

    /**
     * 按包名前缀粗略分类：java、javax、jdk 为 JDK，org、com 为第三方，其余为应用。
     * <p>这是名称启发式规则，com 开头的业务类也会被归为第三方。</p>
     *
     * @param fqcn 非 null 的完整类名
     * @return JDK、3rd 或 app
     */
    private static String categoryOf(String fqcn) {
        if (fqcn.startsWith("java.") || fqcn.startsWith("javax.") || fqcn.startsWith("jdk."))
            return "JDK";
        if (fqcn.startsWith("org.") || fqcn.startsWith("com."))
            return "3rd";
        return "app";
    }

    /**
     * 按 1024 进位格式化字节数；KB 保留一位小数，MB 和 GB 保留两位。
     * <p>数字格式遵循当前默认区域设置。</p>
     *
     * @param sizeInBytes 待格式化的字节数
     * @return 包含数值与单位的文本
     */
    private static String formatSize(long sizeInBytes) {
        if (sizeInBytes < 1024)
            return sizeInBytes + "B";
        else if (sizeInBytes < 1024 * 1024)
            return String.format("%.1fKB", sizeInBytes / 1024.0);
        else if (sizeInBytes < 1024 * 1024 * 1024)
            return String.format("%.2fMB", sizeInBytes / (1024.0 * 1024));
        else
            return String.format("%.2fGB", sizeInBytes / (1024.0 * 1024 * 1024));
    }

    /**
     * 将内部类别代码转换为中文展示标签。
     *
     * @param category 内部类别代码
     * @return 类别标签；未知代码返回“未知”
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
     * 按最后一个点号提取包名，不解析数组类型描述符。
     *
     * @param className 待判断或提取信息的类名
     * @return 包名；无点号时为“默认包”
     */
    private static String extractPackageName(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0)
            return className.substring(0, lastDot);

        return "默认包";
    }

    /**
     * 按类名特征依次识别数组、Lambda、内部类、集合类和基础类型。
     * <p>仅用于展示，不检查类型继承或实际接口。</p>
     *
     * @param className 待判断或提取信息的类名
     * @return 对象类型的中文标签
     */
    private static String determineObjectType(String className) {
        if (className.contains("["))
            return "数组";
        else if (className.contains("$")) {
            if (className.contains("Lambda"))
                return "Lambda表达式";
            else
                return "内部类";
        } else if (className.startsWith("java.util.") &&
                (className.contains("List") || className.contains("Set") || className.contains("Map")))
            return "集合类";
        else if (className.startsWith("java.lang."))
            return "基础类型";
        else
            return "普通类";
    }

    /**
     * 维护按浅表大小排序的小顶堆候选集。
     * <p>达到容量后只用更大对象替换最小对象；相同大小保留原候选。
     * 后续传入更小容量时不会主动缩减已经存在的队列。</p>
     *
     * @param topInstances 按浅表大小升序比较的小顶堆，将被直接修改
     * @param instance     待分析的堆实例
     * @param maxSize      本次维护使用的正容量参数
     */
    private void addToTopInstances(PriorityQueue<Instance> topInstances, Instance instance, int maxSize) {
        if (topInstances.size() < maxSize)
            topInstances.offer(instance);
        else if (instance.getSize() > topInstances.peek().getSize()) {
            topInstances.poll();
            topInstances.offer(instance);
        }
    }

    /**
     * 使用小顶堆选出浅表大小最大的 N 个实例。
     * <p>输入数量不大于 N 时直接返回原列表，不排序也不复制；否则返回降序新列表。
     * 当前主解析流程未调用此辅助方法。</p>
     *
     * @param instances 待处理的实例列表
     * @param n         需要保留的实例数量，应为正数
     * @return 原列表或选出的降序实例列表
     */
    private List<Instance> quickSelectTopN(List<Instance> instances, int n) {
        if (instances.size() <= n)
            return instances;

        // 使用优先队列（小顶堆）来维护Top-N
        PriorityQueue<Instance> topN = new PriorityQueue<>(Comparator.comparingLong(Instance::getSize));
        int processed = 0;

        for (Instance instance : instances) {
            if (topN.size() < n)
                topN.offer(instance);
            else if (instance.getSize() > topN.peek().getSize()) {
                topN.poll();
                topN.offer(instance);
            }

            if (++processed % 10000 == 0) // 每处理10000个对象记录一次进度
                log.debug("快速选择进度: {}/{}", processed, instances.size());
        }

        // 将结果转换为List并按大小降序排序
        List<Instance> result = new ArrayList<>(topN);
        result.sort(Comparator.comparingLong(Instance::getSize).reversed());

        log.info("快速选择完成，从{}个对象中选出{}个最大对象", instances.size(), result.size());
        return result;
    }

    /**
     * 通过名称判断是否可能为常见系统类、反射类、代理类或多维数组。
     * <p>当前主流程中的相关筛选未启用，此方法不影响当前统计结果。</p>
     *
     * @param className 待判断或提取信息的类名
     * @return 符合启发式规则时为 true
     */
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

    // 删除重复定义的 formatSize 方法，在其他地方已定义

    /**
     * 返回基本类型的估算字节数；不包含对象头、对齐或引用压缩因素。
     *
     * @param type 基本类型名称
     * @return 基本类型字节数；未知类型默认按 4 字节处理
     */
    private long getPrimitiveTypeSize(String type) {
        switch (type) {
            case "boolean":
            case "byte":
                return 1;
            case "char":
            case "short":
                return 2;
            case "int":
            case "float":
                return 4;
            case "long":
            case "double":
                return 8;
            default:
                return 4; // 默认值
        }
    }

    /**
     * 估算字段槽的浅表占用：对象引用固定按 8 字节，基本类型按类型宽度。
     * <p>不探测 JVM 的压缩指针配置，因此是展示估算值。</p>
     *
     * @param fieldValue 待估算的字段值
     * @return 估算的字段槽字节数
     */
    private long getFieldSize(FieldValue fieldValue) {
        if (fieldValue instanceof ObjectFieldValue)
            // 对于对象引用字段，返回引用本身的大小（指针大小），而不是被引用对象的大小
            // 在64位JVM中，对象引用通常是8字节；在32位JVM中通常是4字节
            // 这里假设是64位JVM
            return 8; // 对象引用的大小
        else
            // 处理非对象字段（基本类型）
            return getPrimitiveTypeSize(fieldValue.getField().getType().getName());
    }

    /**
     * 将实例字段写入节点明细，数组分别进入数组分析分支。
     * <p>字段按估算大小排序，可能修改解析库返回的列表顺序；
     * 对象头通过浅表大小减去字段槽总和估算，负值时生成大小警告条目。
     * 异常记录日志并保留已生成的部分明细。当前主流程使用实例明细分支。</p>
     *
     * @param instance 待分析的堆实例
     * @param node     接收字段明细的节点，将被修改
     */
    private void analyzeInstanceFieldsForNode(Instance instance, Node node) {
        try {
            // 获取实例的所有字段值
            List<FieldValue> fieldValues = instance.getFieldValues();
            if (fieldValues == null || fieldValues.isEmpty()) {
                // 处理数组类型
                if (instance instanceof ObjectArrayInstance)
                    analyzeArrayInstanceForNode((ObjectArrayInstance) instance, node);
                else if (instance instanceof PrimitiveArrayInstance)
                    analyzePrimitiveArrayInstanceForNode((PrimitiveArrayInstance) instance, node);
                return;
            }

            // 计算所有字段的总大小（用于计算占比）
            long totalObjectSize = instance.getSize();

            // 计算所有字段的实际大小之和
            long actualFieldsSize = fieldValues.stream().mapToLong(this::getFieldSize).sum();

            // 计算对象头部大小（对象总大小 - 字段大小）
            long objectHeaderSize = totalObjectSize - actualFieldsSize;

            // 按字段大小排序（从大到小）
            fieldValues.sort((f1, f2) -> {
                long size1 = getFieldSize(f1);
                long size2 = getFieldSize(f2);
                return Long.compare(size2, size1);
            });

            // 添加对象头部信息作为第一个字段
            // 注意：如果字段大小之和超过对象总大小，说明存在重复计算或其他问题
            if (objectHeaderSize > 0) {
                double headerPercent = totalObjectSize > 0 ? (double) objectHeaderSize / totalObjectSize * 100.0 : 0.0;
                FieldInfo headerInfo = new FieldInfo(
                        "<object header>",
                        "Object Header",
                        "对象头部信息（类指针、标记字等）",
                        objectHeaderSize,
                        formatSize(objectHeaderSize),
                        objectHeaderSize, // 深度大小等于浅表大小
                        formatSize(objectHeaderSize),
                        headerPercent,
                        headerPercent, // 深度大小占比等于浅表大小占比
                        false,
                        false
                );
                node.fields.add(headerInfo);
            } else if (objectHeaderSize < 0) {
                // 如果对象头部大小为负数，说明字段大小之和超过了对象总大小
                // 这种情况下，我们显示一个警告信息
                FieldInfo warningInfo = new FieldInfo(
                        "<size warning>",
                        "Warning",
                        "字段大小之和(" + formatSize(actualFieldsSize) + ")超过对象总大小(" + formatSize(totalObjectSize) + ")",
                        0,
                        "0B",
                        0,
                        "0B",
                        0.0,
                        0.0,
                        false,
                        false
                );
                node.fields.add(warningInfo);
            }

            // 分析每个字段
            for (FieldValue fieldValue : fieldValues) {
                String fieldName = fieldValue.getField().getName();
                String fieldType = fieldValue.getField().getType().getName();
                // 判断是否为基本类型
                boolean isPrimitive = isPrimitiveType(fieldValue.getField().getType().getName());
                boolean isReference = !isPrimitive;

                // 获取字段值和大小
                String valueStr;
                long fieldSize = getFieldSize(fieldValue); // 使用统一的字段大小计算方法

                if (fieldValue instanceof ObjectFieldValue) {
                    ObjectFieldValue objField = (ObjectFieldValue) fieldValue;
                    Instance fieldInstance = objField.getInstance();

                    if (fieldInstance != null)
                        valueStr = fieldInstance.getJavaClass().getName() + "@" + fieldInstance.getInstanceId();
                    else {
                        valueStr = "null";
                        fieldSize = 0; // null引用的大小为0
                    }
                } else
                    valueStr = String.valueOf(fieldValue.getValue());  // 处理非对象字段（基本类型）

                // 计算字段在对象中的占比
                double sizePercent = totalObjectSize > 0 ? (double) fieldSize / totalObjectSize * 100.0 : 0.0;
                long fieldRetainedSize = fieldSize;// 对于Node的字段分析，深度大小等于浅表大小（简化处理）

                // 创建字段信息对象
                FieldInfo fieldInfo = new FieldInfo(
                        fieldName,
                        fieldType,
                        valueStr,
                        fieldSize,
                        formatSize(fieldSize),
                        fieldRetainedSize,
                        formatSize(fieldRetainedSize),
                        sizePercent,
                        sizePercent,
                        isPrimitive,
                        isReference
                );

                // 添加到节点的字段列表
                node.fields.add(fieldInfo);
            }

            // 不重新排序字段，保持对象头部信息在最前面
            // 字段已经在添加前按大小排序过了

        } catch (Exception e) {
            log.warn("分析实例字段失败: {}, 错误: {}", instance.getJavaClass().getName(), e.getMessage());
        }
    }

    /**
     * 生成节点对象数组摘要和最多前 10 个元素的明细。
     * <p>元素大小使用元素对象浅表大小而非引用槽；此分支占比使用比值。
     * 剩余元素生成省略条目，异常记录日志。</p>
     *
     * @param arrayInstance 待分析的数组实例
     * @param node          接收字段明细的节点，将被修改
     */
    private void analyzeArrayInstanceForNode(ObjectArrayInstance arrayInstance, Node node) {
        try {
            int length = arrayInstance.getLength();
            long totalSize = arrayInstance.getSize();

            // 获取数组元素类型
            String elementType = arrayInstance.getJavaClass().getName();
            if (elementType.startsWith("["))
                elementType = elementType.substring(1); // 去掉前面的'['

            // 创建数组概览字段
            FieldInfo arrayInfo = new FieldInfo(
                    "array",
                    elementType + "[]",
                    "长度: " + length,
                    totalSize,
                    formatSize(totalSize),
                    totalSize, // 深度大小等于浅表大小
                    formatSize(totalSize),
                    1.0, // 100%
                    1.0, // 深度大小占比也是100%
                    false,
                    true
            );
            node.fields.add(arrayInfo);

            // 分析前10个元素（或更少）
            int elementsToShow = Math.min(length, 10);
            for (int i = 0; i < elementsToShow; i++) {
                Instance element = arrayInstance.getValues().get(i);
                String valueStr = element != null ?
                        element.getJavaClass().getName() + "@" + element.getInstanceId() : "null";
                long elementSize = element != null ? element.getSize() : 0;

                double elementPercent = totalSize > 0 ? (double) elementSize / totalSize : 0.0;
                FieldInfo elementInfo = new FieldInfo(
                        "[" + i + "]",
                        elementType,
                        valueStr,
                        elementSize,
                        formatSize(elementSize),
                        elementSize, // 深度大小等于浅表大小
                        formatSize(elementSize),
                        elementPercent,
                        elementPercent, // 深度大小占比等于浅表大小占比
                        false,
                        true
                );
                node.fields.add(elementInfo);
            }

            // 如果数组很长，添加一个表示剩余元素的条目
            if (length > 10) {
                FieldInfo remainingInfo = new FieldInfo(
                        "[...]",
                        elementType,
                        "剩余 " + (length - 10) + " 个元素",
                        0,
                        "N/A",
                        0, // 深度大小
                        "N/A",
                        0.0,
                        0.0, // 深度大小占比
                        false,
                        true
                );
                node.fields.add(remainingInfo);
            }
        } catch (Exception e) {
            log.warn("分析数组实例失败: {}, 错误: {}", arrayInstance.getJavaClass().getName(), e.getMessage());
        }
    }

    /**
     * 生成节点基本类型数组摘要；较短 char 数组尝试生成文本预览。
     * <p>摘要的浅表大小与保留大小均使用数组自身大小，占比为 1；
     * 字符转换失败时忽略预览，其他异常记录日志。</p>
     *
     * @param arrayInstance 待分析的数组实例
     * @param node          接收字段明细的节点，将被修改
     */
    private void analyzePrimitiveArrayInstanceForNode(PrimitiveArrayInstance arrayInstance, Node node) {
        try {
            int length = arrayInstance.getLength();
            long totalSize = arrayInstance.getSize();
            String elementType = arrayInstance.getJavaClass().getName();

            // 创建数组概览字段
            FieldInfo arrayInfo = new FieldInfo(
                    "array",
                    elementType,
                    "长度: " + length,
                    totalSize,
                    formatSize(totalSize),
                    totalSize, // 深度大小等于浅表大小
                    formatSize(totalSize),
                    1.0, // 100%
                    1.0, // 深度大小占比也是100%
                    true,
                    false
            );
            node.fields.add(arrayInfo);

            // 对于基本类型数组，只显示长度和总大小信息
            // 如果是字符数组，可以尝试显示内容
            if (elementType.equals("char[]") && length <= 100) {
                try {
                    // 获取字符数组内容
                    char[] chars = new char[length];
                    List<Object> values = arrayInstance.getValues();
                    for (int i = 0; i < length; i++) {
                        Object value = values.get(i);
                        if (value instanceof Character)
                            chars[i] = (Character) value;
                    }

                    String content = new String(chars);

                    if (content.length() > 50)
                        content = content.substring(0, 47) + "...";

                    FieldInfo contentInfo = new FieldInfo(
                            "content",
                            "String",
                            content,
                            0,
                            "N/A",
                            0, // 深度大小
                            "N/A",
                            0.0,
                            0.0, // 深度大小占比
                            true,
                            false
                    );
                    node.fields.add(contentInfo);
                } catch (Exception e) {
                    // 忽略字符数组内容获取错误
                }
            }
        } catch (Exception e) {
            log.warn("分析基本类型数组失败: {}, 错误: {}", arrayInstance.getJavaClass().getName(), e.getMessage());
        }
    }

    /**
     * 填充大实例的字段明细，包含估算对象头、基本类型值及引用目标信息。
     * <p>普通引用槽按 8 字节估算，引用保留大小读取失败时回退到槽大小；
     * 字段保留大小之间可能重叠，不能作为精确的内存分摊。占比使用百分数。
     * 字段按槽大小排序，异常记录日志并保留部分结果。</p>
     *
     * @param instance      待分析的堆实例
     * @param classInstance 接收字段明细的实例模型，将被修改
     */
    private void analyzeInstanceFields(Instance instance, ClassInstance classInstance) {
        try {
            // 获取实例的所有字段值
            List<FieldValue> fieldValues = instance.getFieldValues();

            if (fieldValues == null || fieldValues.isEmpty()) {
                // 处理数组类型
                if (instance instanceof ObjectArrayInstance)
                    analyzeArrayInstance((ObjectArrayInstance) instance, classInstance);
                else if (instance instanceof PrimitiveArrayInstance)
                    analyzePrimitiveArrayInstance((PrimitiveArrayInstance) instance, classInstance);

                return;
            }

            long totalObjectSize = instance.getSize();// 计算所有字段的总大小（用于计算占比）
            long totalRetainedSize;

            try {
                totalRetainedSize = instance.getRetainedSize();
                // 确保深度大小至少等于浅表大小
                if (totalRetainedSize < totalObjectSize)
                    totalRetainedSize = totalObjectSize;
            } catch (Exception e) {
                log.debug("获取实例深度大小失败，使用浅表大小: {}", instance.getJavaClass().getName());
                totalRetainedSize = totalObjectSize;
            }

            // 计算所有字段的实际大小之和
            long actualFieldsSize = fieldValues.stream().mapToLong(this::getFieldSize).sum();

            // 计算对象头部大小（对象总大小 - 字段大小）
            long objectHeaderSize = totalObjectSize - actualFieldsSize;

            // 按字段大小排序（从大到小）
            fieldValues.sort((f1, f2) -> {
                long size1 = getFieldSize(f1);
                long size2 = getFieldSize(f2);
                return Long.compare(size2, size1);
            });

            // 添加对象头部信息作为第一个字段
            if (objectHeaderSize > 0) {
                double headerPercent = totalObjectSize > 0 ? (double) objectHeaderSize / totalObjectSize * 100.0 : 0.0;
                double headerRetainedPercent = totalRetainedSize > 0 ? (double) objectHeaderSize / totalRetainedSize * 100.0 : 0.0;
                FieldInfo headerInfo = new FieldInfo(
                        "<object header>",
                        "Object Header",
                        "对象头部信息（类指针、标记字等）",
                        objectHeaderSize,
                        formatSize(objectHeaderSize),
                        objectHeaderSize, // 深度大小等于浅表大小
                        formatSize(objectHeaderSize),
                        headerPercent,
                        headerRetainedPercent,
                        false,
                        false
                );
                classInstance.fields.add(headerInfo);
            }

            // 分析每个字段
            for (FieldValue fieldValue : fieldValues) {
                String fieldName = fieldValue.getField().getName();
                String fieldType = fieldValue.getField().getType().getName();
                boolean isPrimitive = isPrimitiveType(fieldType);
                boolean isReference = !isPrimitive;

                // 获取字段值和大小
                String valueStr;
                long fieldSize = getFieldSize(fieldValue); // 使用统一的字段大小计算方法
                long fieldRetainedSize;

                if (fieldValue instanceof ObjectFieldValue) {
                    ObjectFieldValue objField = (ObjectFieldValue) fieldValue;
                    Instance fieldInstance = objField.getInstance();

                    if (fieldInstance != null) {
                        // 计算字段的深度大小
                        try {
                            fieldRetainedSize = fieldInstance.getRetainedSize();
                            // 确保深度大小至少等于浅表大小
                            if (fieldRetainedSize < fieldSize)
                                fieldRetainedSize = fieldSize;
                        } catch (Exception e) {
                            log.debug("获取字段深度大小失败: {}, 使用浅表大小", fieldName);
                            fieldRetainedSize = fieldSize;
                        }
                        valueStr = fieldInstance.getJavaClass().getName() + "@" + fieldInstance.getInstanceId();
                    } else {
                        valueStr = "null";
                        fieldSize = 0; // null引用的大小为0
                        fieldRetainedSize = 0;
                    }
                } else {
                    // 处理非对象字段（基本类型）
                    valueStr = String.valueOf(fieldValue.getValue());
                    fieldRetainedSize = fieldSize; // 基本类型的深度大小等于浅表大小
                }

                // 计算字段在对象中的占比
                double sizePercent = totalObjectSize > 0 ? (double) fieldSize / totalObjectSize * 100.0 : 0.0;
                double retainedSizePercent = totalRetainedSize > 0 ? (double) fieldRetainedSize / totalRetainedSize * 100.0 : 0.0;

                // 创建字段信息对象
                FieldInfo fieldInfo = new FieldInfo(
                        fieldName,
                        fieldType,
                        valueStr,
                        fieldSize,
                        formatSize(fieldSize),
                        fieldRetainedSize,
                        formatSize(fieldRetainedSize),
                        sizePercent,
                        retainedSizePercent,
                        isPrimitive,
                        isReference
                );

                // 添加到实例的字段列表
                classInstance.fields.add(fieldInfo);
            }

            // 不重新排序字段，保持对象头部信息在最前面
            // 字段已经在添加前按大小排序过了
        } catch (Exception e) {
            log.warn("分析实例字段失败: {}, 错误: {}", instance.getJavaClass().getName(), e.getMessage());
        }
    }

    /**
     * 填充对象数组长度、前 10 个非空元素位置及剩余项摘要。
     * <p>仅检查前 10 个索引；元素采用目标对象浅表大小，未计算其完整保留大小。
     * 剩余摘要按数组大小减去长度字段估算，未扣除已显示元素，不可直接相加求和。</p>
     *
     * @param arrayInstance 待分析的数组实例
     * @param classInstance 接收字段明细的实例模型，将被修改
     */
    private void analyzeArrayInstance(ObjectArrayInstance arrayInstance, ClassInstance classInstance) {
        int length = arrayInstance.getLength();
        long totalSize = arrayInstance.getSize();
        String elementType = arrayInstance.getJavaClass().getName();  // 获取数组元素类型

        if (elementType.startsWith("["))
            elementType = elementType.substring(1); // 去掉前面的'['

        // 创建数组概览字段
        double lengthPercent = (double) 4 / totalSize * 100.0;
        FieldInfo arrayInfo = new FieldInfo(
                "length", "int", String.valueOf(length),
                4, // int类型大小
                "4B", 4, // 深度大小等于浅表大小
                "4B", lengthPercent, lengthPercent, // 深度大小占比等于浅表大小占比
                true, false
        );
        classInstance.fields.add(arrayInfo);

        int maxElements = Math.min(10, length);// 分析数组元素（最多10个）
        List<Instance> elements = arrayInstance.getValues();

        for (int i = 0; i < maxElements; i++) {
            Instance element = elements.get(i);
            if (element == null)
                continue;

            long elementSize = element.getSize();
            String elementValue = element.getJavaClass().getName() + "@" + element.getInstanceId();

            double elementPercent = (double) elementSize / totalSize * 100.0;
            FieldInfo elementInfo = new FieldInfo(
                    "[" + i + "]",
                    elementType,
                    elementValue,
                    elementSize,
                    formatSize(elementSize),
                    elementSize, // 深度大小等于浅表大小
                    formatSize(elementSize),
                    elementPercent,
                    elementPercent, // 深度大小占比等于浅表大小占比
                    false,
                    true
            );
            classInstance.fields.add(elementInfo);
        }

        // 如果数组元素很多，添加一个摘要信息
        if (length > 10) {
            long summarySize = totalSize - 4; // 减去length字段的大小
            double summaryPercent = (double) summarySize / totalSize * 100.0;
            FieldInfo summaryInfo = new FieldInfo(
                    "...",
                    elementType,
                    "还有" + (length - 10) + "个元素",
                    summarySize,
                    formatSize(summarySize),
                    summarySize, // 深度大小等于浅表大小
                    formatSize(summarySize),
                    summaryPercent,
                    summaryPercent, // 深度大小占比等于浅表大小占比
                    false,
                    true
            );
            classInstance.fields.add(summaryInfo);
        }
    }

    /**
     * 生成基本类型数组的长度、元素总量和可选字节预览。
     * <p>根据 JVM 数组描述符估算元素宽度，长度按 4 字节计，不补计对象头与对齐。
     * 字节数组最多展示前 20 个元素的十六进制文本，异常记录日志。</p>
     *
     * @param arrayInstance 待分析的数组实例
     * @param classInstance 接收字段明细的实例模型，将被修改
     */
    private void analyzePrimitiveArrayInstance(PrimitiveArrayInstance arrayInstance, ClassInstance classInstance) {
        int length = arrayInstance.getLength();
        long totalSize = arrayInstance.getSize();
        String arrayType = arrayInstance.getJavaClass().getName();
        String elementType = ""; // 获取元素类型
        int elementSize = 0;

        try {
            if (arrayType.equals("[Z")) {
                elementType = "boolean";
                elementSize = 1;
            } else if (arrayType.equals("[B")) {
                elementType = "byte";
                elementSize = 1;
            } else if (arrayType.equals("[C")) {
                elementType = "char";
                elementSize = 2;
            } else if (arrayType.equals("[S")) {
                elementType = "short";
                elementSize = 2;
            } else if (arrayType.equals("[I")) {
                elementType = "int";
                elementSize = 4;
            } else if (arrayType.equals("[J")) {
                elementType = "long";
                elementSize = 8;
            } else if (arrayType.equals("[F")) {
                elementType = "float";
                elementSize = 4;
            } else if (arrayType.equals("[D")) {
                elementType = "double";
                elementSize = 8;
            }

            // 创建数组概览字段
            double lengthPercent = (double) 4 / totalSize * 100.0;
            FieldInfo arrayInfo = new FieldInfo(
                    "length",
                    "int",
                    String.valueOf(length),
                    4, // int类型大小
                    "4B",
                    4L, // 深度大小等于浅表大小
                    "4B",
                    lengthPercent,
                    lengthPercent, // 深度大小占比等于浅表大小占比
                    true,
                    false
            );
            classInstance.fields.add(arrayInfo);

            long elementsSize = (long) length * elementSize; // 添加数组元素总大小信息
            double elementsPercent = (double) elementsSize / totalSize * 100.0;
            FieldInfo elementsInfo = new FieldInfo(
                    "elements",
                    elementType + "[]",
                    length + "个" + elementType + "元素",
                    elementsSize,
                    formatSize(elementsSize),
                    elementsSize, // 深度大小等于浅表大小
                    formatSize(elementsSize),
                    elementsPercent,
                    elementsPercent, // 深度大小占比等于浅表大小占比
                    true,
                    false
            );
            classInstance.fields.add(elementsInfo);

            if (arrayType.equals("[B") && length > 0) {// 对于byte[]数组，尝试显示前20个字节的内容
                List<String> values = new ArrayList<>();
                int maxShow = Math.min(20, length);
                byte[] bytes = new byte[maxShow];// 获取字节数组内容

                try {
                    for (int i = 0; i < maxShow; i++) { // 尝试通过反射获取数组内容
                        Object value = arrayInstance.getValues().get(i);
                        if (value instanceof Byte)
                            bytes[i] = (Byte) value;
                    }
                } catch (Exception e) {
                    log.warn("获取字节数组内容失败: {}", e.getMessage());
                }

                for (int i = 0; i < maxShow; i++)
                    values.add(String.format("%02X", bytes[i]));

                String preview = String.join(" ", values) + (length > 20 ? "..." : "");
                FieldInfo previewInfo = new FieldInfo("preview", "hex",
                        preview, 0, // 不计入大小
                        "0B", 0L, // 深度大小等于浅表大小
                        "0B", 0.0,
                        0.0, // 深度大小占比等于浅表大小占比
                        true, false
                );
                classInstance.fields.add(previewInfo);
            }
        } catch (Exception e) {
            log.warn("分析基本类型数组失败: {}, 错误: {}", arrayInstance.getJavaClass().getName(), e.getMessage());
        }
    }

    // 已在上面定义了 getFieldSize 方法，这里删除重复定义

    /**
     * 判断名称是否为八种 Java 基本类型之一，不包含 void。
     *
     * @param typeName 待判断的类型名称
     * @return 为受支持的基本类型名称时返回 true
     */
    private boolean isPrimitiveType(String typeName) {
        return typeName.equals("boolean") ||
                typeName.equals("byte") ||
                typeName.equals("char") ||
                typeName.equals("short") ||
                typeName.equals("int") ||
                typeName.equals("long") ||
                typeName.equals("float") ||
                typeName.equals("double");
    }

    // 已在上面定义了 getPrimitiveTypeSize 方法，这里删除重复定义

    /**
     * 按类名推断较细的 JDK、Java EE、Spring、应用或数组类别。
     * <p>当前主解析流程使用另一套类别规则，此方法保留供辅助分析使用。</p>
     *
     * @param className 待判断或提取信息的类名
     * @return 英文分类标签
     */
    private String determineCategory(String className) {
        if (className.startsWith("java.lang."))
            return "Java Lang";
        if (className.startsWith("java.util."))
            return "Java Util";
        if (className.startsWith("java.io."))
            return "Java IO";
        if (className.startsWith("java.net."))
            return "Java Net";
        if (className.startsWith("java.sql."))
            return "Java SQL";
        if (className.startsWith("javax."))
            return "Java EE";
        if (className.startsWith("org.springframework."))
            return "Spring";
        if (className.startsWith("com.example."))
            return "Application";
        if (className.contains("[]."))
            return "Array";

        return "Other";
    }

    /**
     * 简化集合类节点的引用展示，并直接修改传入图模型。
     * <p>识别集合后移除其出边，以原出边数量更新标签和对象类型；
     * 数量是图边数而非真实集合元素数。当前实现不向图中追加聚合节点或替代边，
     * 也不重新计算内存统计。</p>
     *
     * @param graph 待简化的图模型，将被直接修改
     */
    private void collapseCollectionLikeNodes(GraphModel graph) {
        Map<String, Integer> collectionElementCount = new HashMap<>();
        Set<String> collectionNodeIds = new HashSet<>();
        Set<Link> linksToRemove = new HashSet<>();
        Map<String, Link> collectionLinks = new HashMap<>();

        // 1. 识别集合类型的节点
        for (Node node : graph.nodes) {
            if (isCollectionType(node.className))
                collectionNodeIds.add(node.id);
        }

        // 2. 统计每个集合的元素数量，并准备聚合连线
        for (Link link : graph.links) {
            if (collectionNodeIds.contains(link.source)) {
                String collectionId = link.source; // 这是从集合指向元素的连线
                collectionElementCount.put(collectionId, collectionElementCount.getOrDefault(collectionId, 0) + 1);
                linksToRemove.add(link);
                String key = collectionId + "->elements"; // 保留一条代表性连线，用于显示聚合信息

                if (!collectionLinks.containsKey(key)) {
                    Node sourceNode = graph.nodes.stream()
                            .filter(n -> n.id.equals(collectionId))
                            .findFirst().orElse(null);

                    if (sourceNode != null)
                        collectionLinks.put(key, new Link(
                                collectionId,
                                "collapsed_" + collectionId,
                                collectionElementCount.get(collectionId) + "个元素"
                        ));
                }
            }
        }

        // 3. 移除原始的集合元素连线
        graph.links.removeAll(linksToRemove);

        // 4. 更新集合节点的显示信息
        for (Node node : graph.nodes) {
            if (collectionNodeIds.contains(node.id)) {
                int elementCount = collectionElementCount.getOrDefault(node.id, 0);

                if (elementCount > 0) {
                    // 更新节点标签，显示元素数量
                    String originalLabel = node.label;
                    node.label = String.format("%s [%d个元素]", originalLabel.split("\\(")[0].trim(), elementCount);
                    node.objectType = node.objectType + " (已折叠)"; // 添加聚合信息到对象类型
                }
            }
        }

        // 5. 移除被折叠的元素节点（可选，这里保留以维持图的完整性）
        // 实际应用中可以选择性移除孤立的元素节点
        log.info("集合折叠完成: {}个集合被处理", collectionElementCount.size());
    }

    /**
     * 逐实例累加保留大小，读取失败或小于浅表大小时使用浅表大小。
     * <p>不会消除不同实例的保留集合重叠，因此结果是排序用汇总指标，
     * 不代表该类独占的去重内存。</p>
     *
     * @param instances 待处理的实例列表
     * @return 带浅表大小下限的保留大小汇总值，单位为字节
     */
    private long calculateTotalRetainedSize(List<Instance> instances) {
        long totalRetainedSize = 0;
        long totalShallowSize = 0;
        int retainedSizeFailures = 0;
        int retainedSizeSuccesses = 0;
        int retainedSmallerCount = 0;  // 特殊分析：记录保留大小比浅表大小小的情况
        long retainedSmallerTotalShallow = 0;
        long retainedSmallerTotalRetained = 0;

        for (Instance instance : instances) {
            long shallowSize = instance.getSize();
            totalShallowSize += shallowSize;

            try {
                long retainedSize = instance.getRetainedSize();// 使用NetBeans API计算保留大小

                // 修复：确保保留大小至少等于浅表大小
                // 在某些情况下，由于共享引用，保留大小可能小于浅表大小  但从用户角度看，对象的深度大小至少应该等于其浅表大小
                if (retainedSize < shallowSize) {
                    retainedSmallerCount++;
                    retainedSmallerTotalShallow += shallowSize;
                    retainedSmallerTotalRetained += retainedSize;

                    int incomingReferences = instance.getReferences().size();   // 分析引用情况
                    log.debug("保留<浅表: 类={}, 浅表={}, 保留={}, 引用数={}",
                            instance.getJavaClass().getName(), formatSize(shallowSize), formatSize(retainedSize), incomingReferences);

                    retainedSize = shallowSize;  // 修复：使用浅表大小作为最小值
                }

                totalRetainedSize += retainedSize;
                retainedSizeSuccesses++;

                if (retainedSize > 1024 * 1024 || retainedSize > shallowSize * 2) // 记录特别大的对象或有显著差异的对象
                    log.info("对象保留大小: 类={}, 浅表={}({}), 保留={}({}), 差异={}倍",
                            instance.getJavaClass().getName(),
                            shallowSize, formatSize(shallowSize),
                            retainedSize, formatSize(retainedSize),
                            String.format("%.2f", (double) retainedSize / shallowSize));
            } catch (Exception e) {
                log.warn("获取保留大小失败: 类={}, 错误={}, 使用浅表大小代替", instance.getJavaClass().getName(), e.getMessage());
                totalRetainedSize += shallowSize; // 回退到浅表大小
                retainedSizeFailures++;
            }
        }

        if (retainedSmallerCount > 0)// 如果有大量保留大小<浅表大小的情况，详细报告
            log.info("保留大小异常分析: {}个对象保留<浅表, 浅表总计={}, 保留总计={}, 已修正为使用浅表大小",
                    retainedSmallerCount,
                    formatSize(retainedSmallerTotalShallow),
                    formatSize(retainedSmallerTotalRetained));

        log.info("保留大小计算统计: 成功={}, 失败={}, 浅表总计={}({}), 保留总计={}({}), 比率={}%",
                retainedSizeSuccesses, retainedSizeFailures,
                totalShallowSize, formatSize(totalShallowSize),
                totalRetainedSize, formatSize(totalRetainedSize),
                String.format("%.1f", (double) totalRetainedSize / totalShallowSize * 100));

        return totalRetainedSize;
    }

    /**
     * 按有限深度递归估算实例可达大小并求和。
     * <p>每个根对象使用独立访问集合，跨根共享对象可能重复计算；最大深度为 5。
     * 此辅助算法不等同于支配树保留大小，当前主流程未使用。</p>
     *
     * @param instances 待处理的实例列表
     * @return 有限深度的可达大小估算总量，单位为字节
     */
    private long calculateTotalDeepSize(List<Instance> instances) {
        long totalDeepSize = 0;

        for (Instance instance : instances) { // 为每个实例单独计算深度大小，不使用全局visited避免遗漏
            Set<Long> instanceVisited = new HashSet<>(); // 每个实例用独立的visited集合
            totalDeepSize += calculateDeepSize(instance, instanceVisited, 0, 5);
        }

        return totalDeepSize;
    }

    /**
     * 累加当前对象与限定深度内引用字段指向对象的浅表大小。
     * <p>通过实例 ID 避免单次遍历中的循环和重复；到达深度上限即停止。
     * 不单独遍历对象数组元素；异常记录日志并返回已累计结果。</p>
     *
     * @param obj      当前递归访问对象，可为 null
     * @param visited  本次根对象遍历已访问的实例 ID 集合，将被修改
     * @param depth    当前深度，根对象从 0 开始
     * @param maxDepth 停止递归的深度上限
     * @return 本次递归累计字节数；空对象、已访问对象或到达深度上限时为 0
     */
    private long calculateDeepSize(Instance obj, Set<Long> visited, int depth, int maxDepth) {
        if (obj == null || depth >= maxDepth)
            return 0;

        long objId = obj.getInstanceId();

        if (visited.contains(objId))
            return 0; // 已经计算过，避免重复

        visited.add(objId);
        long totalSize = obj.getSize(); // 从浅表大小开始

        try {
            // 遍历所有对象字段
            for (FieldValue fieldValue : obj.getFieldValues()) {
                if (fieldValue instanceof ObjectFieldValue) {
                    ObjectFieldValue objFieldValue = (ObjectFieldValue) fieldValue;
                    Instance referencedObj = objFieldValue.getInstance();

                    if (referencedObj != null) {
                        long refSize = calculateDeepSize(referencedObj, visited, depth + 1, maxDepth);
                        totalSize += refSize;

                        // 记录大对象引用
                        if (refSize > 100 * 1024) // 大于100KB的引用
                            log.debug("发现大对象引用: {} -> {}, 大小: {}", obj.getJavaClass().getName(),
                                    referencedObj.getJavaClass().getName(), formatSize(refSize));
                    }
                }
            }
        } catch (Exception e) {
            // 如果访问字段失败，记录日志但继续
            log.debug("计算深度大小时访问对象字段失败: {}, 对象类型: {}", e.getMessage(), obj.getJavaClass().getName());
        }

        return totalSize;
    }

    /**
     * 根据 Lambda、CGLIB 与代理名称片段识别部分运行时生成类。
     *
     * @param className 待判断或提取信息的类名
     * @return 名称命中已知生成类片段时为 true
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
     * 按名称包含关系识别常见 List、Map、Set 及部分并发集合实现。
     * <p>不检查继承关系，不能识别全部集合类型，也可能匹配名称相似的普通类。</p>
     *
     * @param className 待判断或提取信息的类名
     * @return 名称命中集合实现片段时为 true
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
