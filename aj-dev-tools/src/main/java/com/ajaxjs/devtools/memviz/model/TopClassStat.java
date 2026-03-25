package com.ajaxjs.devtools.memviz.model;


import java.util.ArrayList;
import java.util.List;

/**
 * Top100类统计信息
 */
public class TopClassStat {
    /**
     * 类名
     */
    public String className;
    /**
     * 短类名
     */
    public String shortName;
    /**
     * 包名
     */
    public String packageName;
    /**
     * 类别
     */
    public String category;
    /**
     * 实例数量
     */
    public int instanceCount;
    /**
     * 该类所有实例的总内存（浅表大小）
     */
    public long totalSize;
    /**
     * 格式化的总内存
     */
    public String formattedTotalSize;
    /**
     * 该类所有实例的总深度大小
     */
    public long totalDeepSize;
    /**
     * 格式化的总深度大小
     */
    public String formattedTotalDeepSize;
    /**
     * 平均每个实例大小（浅表）
     */
    public long avgSize;
    /**
     * 格式化的平均大小
     */
    public String formattedAvgSize;
    /**
     * 平均每个实例深度大小
     */
    public long avgDeepSize;
    /**
     * 格式化的平均深度大小
     */
    public String formattedAvgDeepSize;
    /**
     * 排名
     */
    public int rank;
    /**
     * 该类中内存占用最大的实例列表
     */
    public List<ClassInstance> topInstances;

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