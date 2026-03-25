package com.ajaxjs.devtools.memviz.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点模型
 * 用于表示内存中的对象或类
 */
public class Node {
    /**
     * objectId 或 class@id
     */
    public String id;
    /**
     * 类名(短)
     */
    public String label;
    /**
     * 类名(全)
     */
    public String className;
    /**
     * 浅表大小
     */
    public long shallowSize;
    /**
     * 深度大小
     */
    public long deepSize;
    /**
     * JDK/第三方/业务
     */
    public String category;
    /**
     * 该类的实例总数
     */
    public int instanceCount;
    /**
     * 格式化的浅表大小显示
     */
    public String formattedSize;
    /**
     * 格式化的深度大小显示
     */
    public String formattedDeepSize;
    /**
     * 包名
     */
    public String packageName;
    /**
     * 是否为数组类型
     */
    public boolean isArray;
    /**
     * 对象类型描述
     */
    public String objectType;
    /**
     * 对象的字段信息
     */
    public List<FieldInfo> fields;

    public Node(String id, String label, String className, long shallowSize, String category) {
        this.id = id;
        this.label = label;
        this.className = className;
        this.shallowSize = shallowSize;
        this.category = category;
        this.fields = new ArrayList<>();
    }

    // 增强构造函数
    public Node(String id, String label, String className, long shallowSize, String category,
                int instanceCount, String formattedSize, String packageName, boolean isArray, String objectType,
                long deepSize, String formattedDeepSize) {
        this.id = id;
        this.label = label;
        this.className = className;
        this.shallowSize = shallowSize;
        this.deepSize = deepSize;
        this.category = category;
        this.instanceCount = instanceCount;
        this.formattedSize = formattedSize;
        this.formattedDeepSize = formattedDeepSize;
        this.packageName = packageName;
        this.isArray = isArray;
        this.objectType = objectType;
        this.fields = new ArrayList<>();
    }
}