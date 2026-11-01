package com.ajaxjs.devtools.memviz.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 图节点，可承载对象信息或类聚合统计。
 * <p>当前主解析流程生成类节点，大小和数量均表示该类的聚合值，
 * 其中 {@code deepSize} 实际使用逐实例保留大小的汇总值。</p>
 */
public class Node {
    /**
     * 图节点标识；主流程使用 class_ 加类名哈希值生成，不保证不同类名绝无哈希碰撞。
     */
    public String id;
    /**
     * 前端展示标签；类节点包含简短类名与统计摘要，折叠时可能追加集合摘要。
     */
    public String label;
    /**
     * HPROF 中记录的完整类名；数组名称保持解析库提供的形式。
     */
    public String className;
    /**
     * 节点浅表大小，单位为字节；类节点为该类全部实例浅表大小之和。
     */
    public long shallowSize;
    /**
     * 节点保留大小汇总值，单位为字节；类节点的实例间可能存在重叠。
     */
    public long deepSize;
    /**
     * 类别标签，由类名规则推断，用于展示分组，不代表实际依赖来源。
     */
    public String category;
    /**
     * 该类在本次统计范围内的实例数量。
     */
    public int instanceCount;
    /**
     * 浅表大小的可读文本，按 1024 进位显示 B、KB、MB 或 GB。
     */
    public String formattedSize;
    /**
     * 保留大小汇总值的格式化文本，使用 B、KB、MB 或 GB。
     */
    public String formattedDeepSize;
    /**
     * 从完整类名提取的包名；没有点号时显示“默认包”。
     */
    public String packageName;
    /**
     * 是否按类名规则识别为数组，不执行 Java 类型反射判断。
     */
    public boolean isArray;
    /**
     * 根据类名推断的对象类型，如数组、集合类、内部类或普通类。
     */
    public String objectType;
    /**
     * 可变字段明细列表；可包含对象头、数组长度和省略项等合成条目。
     */
    public List<FieldInfo> fields;

    /**
     * 创建图节点，保存传入的展示数据。
     * <p>字段明细初始化为空的可变列表，后续由解析服务填充。</p>
     *
     * @param id          节点或对象标识。对象明细使用堆实例 ID 的十进制字符串；类节点使用类名哈希构造标识。
     * @param label       前端展示标签；类节点包含简短类名与统计摘要，折叠时可能追加集合摘要。
     * @param className   HPROF 中记录的完整类名；数组名称保持解析库提供的形式。
     * @param shallowSize 节点浅表大小，单位为字节；类节点为该类全部实例浅表大小之和。
     * @param category    类别标签，由类名规则推断，用于展示分组，不代表实际依赖来源。
     */
    public Node(String id, String label, String className, long shallowSize, String category) {
        this.id = id;
        this.label = label;
        this.className = className;
        this.shallowSize = shallowSize;
        this.category = category;
        this.fields = new ArrayList<>();
    }

    // 增强构造函数

    /**
     * 创建图节点，保存传入的展示数据。
     * <p>字段明细初始化为空的可变列表，后续由解析服务填充。</p>
     *
     * @param id                节点或对象标识。对象明细使用堆实例 ID 的十进制字符串；类节点使用类名哈希构造标识。
     * @param label             前端展示标签；类节点包含简短类名与统计摘要，折叠时可能追加集合摘要。
     * @param className         HPROF 中记录的完整类名；数组名称保持解析库提供的形式。
     * @param shallowSize       节点浅表大小，单位为字节；类节点为该类全部实例浅表大小之和。
     * @param category          类别标签，由类名规则推断，用于展示分组，不代表实际依赖来源。
     * @param instanceCount     该类在本次统计范围内的实例数量。
     * @param formattedSize     浅表大小的可读文本，按 1024 进位显示 B、KB、MB 或 GB。
     * @param packageName       从完整类名提取的包名；没有点号时显示“默认包”。
     * @param isArray           是否按类名规则识别为数组，不执行 Java 类型反射判断。
     * @param objectType        根据类名推断的对象类型，如数组、集合类、内部类或普通类。
     * @param deepSize          节点保留大小汇总值，单位为字节；类节点的实例间可能存在重叠。
     * @param formattedDeepSize 保留大小汇总值的格式化文本，使用 B、KB、MB 或 GB。
     */
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