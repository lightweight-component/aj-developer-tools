package com.ajaxjs.devtools.memviz.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 堆分析结果，包含类聚合节点、抽样引用边和按保留大小排序的类统计。
 * <p>全局计数覆盖类名筛选器接受的实例；节点与排行榜还会排除部分生成类，
 * 并限制数量，因此全局总量不一定等于展示节点之和。</p>
 */
public class GraphModel {
    /**
     * 最终展示节点列表，默认初始化为空列表；主流程最多保留 100 个类节点。
     */
    public List<Node> nodes = new ArrayList<>();

    /**
     * 抽样得到的类间引用边，最多保留 200 条；集合折叠可能移除部分边。
     */
    public List<Link> links = new ArrayList<>();

    /**
     * 按保留大小汇总值降序排列的类统计，最多 100 项。
     */
    public List<TopClassStat> top100Classes = new ArrayList<>();
    /**
     * 类名筛选器接受的全部实例数量，包含未进入最终节点列表的对象。
     */
    public int totalObjects;
    /**
     * 通过筛选的全部实例浅表大小之和，单位为字节，不包含被引用对象的额外累加。
     */
    public long totalMemory;
    /**
     * 全局浅表大小总量的可读文本。
     */
    public String formattedTotalMemory;

}