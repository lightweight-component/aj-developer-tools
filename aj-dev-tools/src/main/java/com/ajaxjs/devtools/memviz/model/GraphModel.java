package com.ajaxjs.devtools.memviz.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 图模型
 * 用于表示内存分析的整体结果
 */
public class GraphModel {
    /**
     * 节点列表
     */
    public List<Node> nodes = new ArrayList<>();

    /**
     * 链接列表
     */
    public List<Link> links = new ArrayList<>();

    /**
     * Top100类统计列表
     */
    public List<TopClassStat> top100Classes = new ArrayList<>();
    /**
     * 总对象数
     */
    public int totalObjects;
    /**
     * 总内存占用
     */
    public long totalMemory;
    /**
     * 格式化的总内存
     */
    public String formattedTotalMemory;

}