package com.ajaxjs.devtools.memviz.model;

import lombok.AllArgsConstructor;

/**
 * 链接模型
 * 用于表示对象之间的引用关系
 */
@AllArgsConstructor
public class Link {
    /**
     * 源节点ID
     */
    public String source;
    /**
     * 目标节点ID
     */
    public String target;
    /**
     * 通过哪个字段/元素引用
     */
    public String field;
}