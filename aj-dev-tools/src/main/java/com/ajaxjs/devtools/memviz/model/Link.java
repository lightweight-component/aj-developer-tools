package com.ajaxjs.devtools.memviz.model;

import lombok.AllArgsConstructor;

/**
 * 图中由源节点指向目标节点的引用边。
 * <p>当前解析流程按源类和目标类去重，以“引用”作为标签；不是逐字段完整记录。
 * Lombok 生成包含全部字段的构造器。</p>
 */
@AllArgsConstructor
public class Link {
    /**
     * 源节点标识，对应节点列表中的 id。
     */
    public String source;
    /**
     * 目标节点标识，对应节点列表中的 id。
     */
    public String target;
    /**
     * 引用边的展示标签；当前类聚合图使用固定文本“引用”。
     */
    public String field;
}