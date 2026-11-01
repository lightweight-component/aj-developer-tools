package com.ajaxjs.devtools.memviz.model;


import java.util.ArrayList;
import java.util.List;

/**
 * 按类聚合的内存统计及大对象明细，用于类排行榜。
 * <p>浅表大小统计对象自身占用；深度大小字段实际汇总各实例的保留大小，
 * 可能重复覆盖同一片对象内存，不是去重后的可达对象总大小。</p>
 */
public class TopClassStat {
    /**
     * HPROF 中记录的完整类名；数组名称保持解析库提供的形式。
     */
    public String className;

    /**
     * 去掉最后一个点号之前部分的类名，用于紧凑显示。
     */
    public String shortName;

    /**
     * 从完整类名提取的包名；没有点号时显示“默认包”。
     */
    public String packageName;

    /**
     * 类别标签，由类名规则推断，用于展示分组，不代表实际依赖来源。
     */
    public String category;

    /**
     * 该类在本次统计范围内的实例数量。
     */
    public int instanceCount;

    /**
     * 该类全部实例的浅表大小之和，单位为字节。
     */
    public long totalSize;

    /**
     * 该类浅表大小总量的可读文本。
     */
    public String formattedTotalSize;

    /**
     * 该类逐实例保留大小的累加值，单位为字节；实例间的内存覆盖可能重叠。
     */
    public long totalDeepSize;

    /**
     * 该类保留大小汇总值的可读文本。
     */
    public String formattedTotalDeepSize;

    /**
     * 平均实例浅表大小，单位为字节，由总量除以数量并进行整数截断。
     */
    public long avgSize;

    /**
     * 平均实例浅表大小的可读文本。
     */
    public String formattedAvgSize;

    /**
     * 平均实例保留大小，单位为字节，由汇总值除以数量并进行整数截断。
     */
    public long avgDeepSize;

    /**
     * 平均实例保留大小的可读文本。
     */
    public String formattedAvgDeepSize;

    /**
     * 从 1 开始的展示排名；类统计在进入最终排行榜时赋值。
     */
    public int rank;

    /**
     * 该类按保留大小降序排列的大实例明细，当前最多 100 项。
     */
    public List<ClassInstance> topInstances;

    /**
     * 创建类统计记录，保存传入的展示数据。
     * <p>实例列表非 null 时直接保存其引用，不进行防御性复制；null 时创建空列表。</p>
     *
     * @param className              HPROF 中记录的完整类名；数组名称保持解析库提供的形式。
     * @param shortName              去掉最后一个点号之前部分的类名，用于紧凑显示。
     * @param packageName            从完整类名提取的包名；没有点号时显示“默认包”。
     * @param category               类别标签，由类名规则推断，用于展示分组，不代表实际依赖来源。
     * @param instanceCount          该类在本次统计范围内的实例数量。
     * @param totalSize              该类全部实例的浅表大小之和，单位为字节。
     * @param formattedTotalSize     该类浅表大小总量的可读文本。
     * @param totalDeepSize          该类逐实例保留大小的累加值，单位为字节；实例间的内存覆盖可能重叠。
     * @param formattedTotalDeepSize 该类保留大小汇总值的可读文本。
     * @param avgSize                平均实例浅表大小，单位为字节，由总量除以数量并进行整数截断。
     * @param formattedAvgSize       平均实例浅表大小的可读文本。
     * @param avgDeepSize            平均实例保留大小，单位为字节，由汇总值除以数量并进行整数截断。
     * @param formattedAvgDeepSize   平均实例保留大小的可读文本。
     * @param rank                   从 1 开始的展示排名；类统计在进入最终排行榜时赋值。
     * @param topInstances           该类按保留大小降序排列的大实例明细，当前最多 100 项。
     */
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