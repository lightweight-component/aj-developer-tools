package com.ajaxjs.devtools.memviz.model;


/**
 * 对象字段或数组摘要的展示条目，也可表示对象头、长度和省略项等合成信息。
 * <p>字段大小是展示估算值：普通引用槽按固定 8 字节计算，数组元素分支可能使用
 * 元素对象大小。保留大小可能重叠，不能将所有条目直接相加作为对象的精确内存。</p>
 */
public class FieldInfo {
    /**
     * 名称；User 中为用户名，字段明细中也可为数组索引或合成条目名称。
     */
    public String name;
    /**
     * 字段类型或合成条目类型的显示名称。
     */
    public String type;
    /**
     * 字段值的文本表示；对象引用通常显示类名和堆实例 ID。
     */
    public String value;
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
     * 浅表占比。实例明细通常使用百分数；旧节点数组分析分支使用 0 至 1 的比值。
     */
    public double sizePercent;
    /**
     * 保留大小占比。实例明细通常使用百分数；旧节点数组分析分支使用比值。
     */
    public double retainedSizePercent;
    /**
     * 该条目是否标记为基本类型；合成条目由各分析分支设置。
     */
    public boolean isPrimitive;
    /**
     * 该条目是否标记为对象引用；与 isPrimitive 一起供前端展示。
     */
    public boolean isReference;

    /**
     * 创建字段展示条目，保存传入的展示数据。
     *
     * @param name                  名称；User 中为用户名，字段明细中也可为数组索引或合成条目名称。
     * @param type                  字段类型或合成条目类型的显示名称。
     * @param value                 字段值的文本表示；对象引用通常显示类名和堆实例 ID。
     * @param size                  浅表大小，单位为字节；字段条目的具体含义取决于普通字段或数组分析分支。
     * @param formattedSize         浅表大小的可读文本，按 1024 进位显示 B、KB、MB 或 GB。
     * @param retainedSize          保留大小或相应展示估算值，单位为字节；不等同于递归可达对象大小。
     * @param formattedRetainedSize 保留大小的可读文本，单位与浅表大小格式相同。
     * @param sizePercent           浅表占比。实例明细通常使用百分数；旧节点数组分析分支使用 0 至 1 的比值。
     * @param retainedSizePercent   保留大小占比。实例明细通常使用百分数；旧节点数组分析分支使用比值。
     * @param isPrimitive           该条目是否标记为基本类型；合成条目由各分析分支设置。
     * @param isReference           该条目是否标记为对象引用；与 isPrimitive 一起供前端展示。
     */
    public FieldInfo(String name, String type, String value, long size,
                     String formattedSize, long retainedSize, String formattedRetainedSize,
                     double sizePercent, double retainedSizePercent,
                     boolean isPrimitive, boolean isReference) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.size = size;
        this.formattedSize = formattedSize;
        this.retainedSize = retainedSize;
        this.formattedRetainedSize = formattedRetainedSize;
        this.sizePercent = sizePercent;
        this.retainedSizePercent = retainedSizePercent;
        this.isPrimitive = isPrimitive;
        this.isReference = isReference;
    }

    // 获取字段浅表大小

    /**
     * 读取条目的浅表大小或相应估算值。
     *
     * @return 条目浅表大小，单位为字节
     */
    public long getSize() {
        return size;
    }

    // 获取字段深度大小

    /**
     * 读取条目的保留大小或相应估算值。
     *
     * @return 条目保留大小，单位为字节
     */
    public long getRetainedSize() {
        return retainedSize;
    }
}