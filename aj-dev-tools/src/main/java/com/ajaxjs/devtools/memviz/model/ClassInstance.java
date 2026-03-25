package com.ajaxjs.devtools.memviz.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 类的实例信息
 */
public class ClassInstance {
    /**
     * 实例ID
     */
    public String id;
    /**
     * 浅表大小
     */
    public long size;
    /**
     * 格式化的浅表大小
     */
    public String formattedSize;
    /**
     * 深度大小（保留大小）
     */
    public long retainedSize;
    /**
     * 格式化的深度大小
     */
    public String formattedRetainedSize;
    /**
     * 在该类中的排名
     */
    public int rank;
    /**
     * 包名
     */
    public String packageName;
    /**
     * 对象类型
     */
    public String objectType;
    /**
     * 是否数组
     */
    public boolean isArray;
    /**
     * 在该类中的内存占比
     */
    public double sizePercentInClass;
    /**
     * 添加字段信息列表
     */
    public List<FieldInfo> fields;

    public ClassInstance(String id, long size, String formattedSize,
                         long retainedSize, String formattedRetainedSize, int rank,
                         String packageName, String objectType, boolean isArray, double sizePercentInClass) {
        this.id = id;
        this.size = size;
        this.formattedSize = formattedSize;
        this.retainedSize = retainedSize;
        this.formattedRetainedSize = formattedRetainedSize;
        this.rank = rank;
        this.packageName = packageName;
        this.objectType = objectType;
        this.isArray = isArray;
        this.sizePercentInClass = sizePercentInClass;
        this.fields = new ArrayList<>();
    }
}