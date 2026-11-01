package com.ajaxjs.devtools.memviz.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个堆对象的展示信息，包括大小、类内排名和字段明细。
 * <p>解析服务为每个类保留最多 100 个较大实例。大小单位为字节，
 * 保留大小读取失败或小于浅表大小时，以浅表大小作为下限。</p>
 */
public class ClassInstance {
    /**
     * HPROF 堆实例 ID 的十进制字符串，用于定位同一快照内的对象。
     */
    public String id;
    /**
     * 浅表大小，单位为字节；字段条目的具体含义取决于普通字段或数组分析分支。
     */
    public long size;
    /**
     * 浅表大小的可读文本，按 1024 进位显示 B、KB、MB 或 GB。
     */
    public String formattedSize;
    /**
     * 保留大小或相应展示估算值，单位为字节；不等同于递归可达对象大小。
     */
    public long retainedSize;
    /**
     * 保留大小的可读文本，单位与浅表大小格式相同。
     */
    public String formattedRetainedSize;
    /**
     * 从 1 开始的展示排名；类统计在进入最终排行榜时赋值。
     */
    public int rank;
    /**
     * 从完整类名提取的包名；没有点号时显示“默认包”。
     */
    public String packageName;
    /**
     * 根据类名推断的对象类型，如数组、集合类、内部类或普通类。
     */
    public String objectType;
    /**
     * 是否按类名规则识别为数组，不执行 Java 类型反射判断。
     */
    public boolean isArray;
    /**
     * 实例保留大小占该类保留大小汇总值的百分数，使用 0 至 100 的展示尺度。
     */
    public double sizePercentInClass;
    /**
     * 可变字段明细列表；可包含对象头、数组长度和省略项等合成条目。
     */
    public List<FieldInfo> fields;

    /**
     * 创建实例展示模型，保存传入的展示数据。
     * <p>字段明细初始化为空的可变列表，后续由解析服务填充。</p>
     *
     * @param id                    节点或对象标识。对象明细使用堆实例 ID 的十进制字符串；类节点使用类名哈希构造标识。
     * @param size                  浅表大小，单位为字节；字段条目的具体含义取决于普通字段或数组分析分支。
     * @param formattedSize         浅表大小的可读文本，按 1024 进位显示 B、KB、MB 或 GB。
     * @param retainedSize          保留大小或相应展示估算值，单位为字节；不等同于递归可达对象大小。
     * @param formattedRetainedSize 保留大小的可读文本，单位与浅表大小格式相同。
     * @param rank                  从 1 开始的展示排名；类统计在进入最终排行榜时赋值。
     * @param packageName           从完整类名提取的包名；没有点号时显示“默认包”。
     * @param objectType            根据类名推断的对象类型，如数组、集合类、内部类或普通类。
     * @param isArray               是否按类名规则识别为数组，不执行 Java 类型反射判断。
     * @param sizePercentInClass    实例保留大小占该类保留大小汇总值的百分数，使用 0 至 100 的展示尺度。
     */
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