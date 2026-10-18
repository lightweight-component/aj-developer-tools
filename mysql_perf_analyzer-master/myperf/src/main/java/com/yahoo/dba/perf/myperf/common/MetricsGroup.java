/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

/**
 * MetricsGroup groups related metrics, for example, metrics can be gathered by a single SQL call.
 * It can contain zero or more subgroups for storage purpose. Each sub group, if exists, will
 * be stored inside a table with the name defined in the sub group definition.
 *
 * @author xrao
 */
public class MetricsGroup {
    /**
     * 保存group name信息。
     */
    @Getter
    @Setter
    private String groupName;//the name will be also used for storage table name.
    /**
     * 标记auto状态。
     */
    @Getter
    @Setter
    private boolean auto = true;//if false, we need manually enable this metric group for a specific db server

    //A top level MetricsGroup can either contains a list of metrics, or a list of sub metrics group,
    //but not both. If sub groups are presented, the data will be stored per sub group and the storage
    //table names will be the groupname of the sub groups.
    /**
     * 保存metrics集合。
     */
    private List<Metric> metrics;
    /**
     * 保存metrics name map集合。
     */
    private Map<String, Metric> metricsNameMap;

    /**
     * 保存sub groups集合。
     */
    @Getter
    private List<MetricsGroup> subGroups = new ArrayList<>();

    /**
     * 标记multiple metrics per row状态。
     */
    @Getter
    @Setter
    private boolean multipleMetricsPerRow = true;//if db returns one row with all required metrics
    /**
     * 保存key column信息。
     */
    @Getter
    @Setter
    private String keyColumn; //some metrics can be per user based (user stats), per disk based, or per network interface based, etc.
    //this will be used to store the key to identify the user, or disk, or network interface
    //For this case, no subgroup is allowed.
    /**
     * 保存metric name column信息。
     */
    @Getter
    @Setter
    private String metricNameColumn;//column name for the metrics name
    /**
     * 保存metric value column信息。
     */
    @Getter
    @Setter
    private String metricValueColumn;//column name for the metrics value
    /**
     * 保存db type信息。
     */
    @Getter
    @Setter
    private String dbType = "MYSQL";
    //The sql used to retrieve the data, either a sql handler id for predefined sql,
    //or full sqlText, but not both, should be specified.
    /**
     * 保存sql信息。
     */
    @Getter
    @Setter
    private String sql;
    /**
     * 保存sql text信息。
     */
    @Getter
    @Setter
    private String sqlText;
    /**
     * 标记store in common table状态。
     */
    @Getter
    @Setter
    private boolean storeInCommonTable = false;//store the metrics in a common shared table
    //better no to be used for subgroup

    /**
     * 保存target table信息。
     */
    @Getter
    @Setter
    private String targetTable;//to save into target metric table, with columns like (dbid, metric_id, ts, value)

    /**
     * 保存parent group信息。
     */
    @Getter
    @Setter
    private MetricsGroup parentGroup;//Since we allow subGroups, each subGroup should have a way to access its parent group
    /**
     * 标记udm flagged状态。
     */
    @Getter
    @Setter
    private boolean udmFlagged;//flag this is user defined metrics group, not builtin

    /**
     * 创建MetricsGroup对象。
     */
    public MetricsGroup() {
        this.metrics = new ArrayList<>();
        this.metricsNameMap = new HashMap<>();
    }

    /**
     * 创建MetricsGroup对象。
     *
     * @param groupName group name参数。
     */
    public MetricsGroup(String groupName) {
        this.groupName = groupName;
        this.metrics = new ArrayList<>();
        this.metricsNameMap = new HashMap<>();
    }

    /**
     * Note the returned list is an unmodified list
     *
     * @return
     */
    public List<Metric> getMetrics() {
        return Collections.unmodifiableList(metrics);
    }

    /**
     * 添加metrics。
     *
     * @param metric metric参数。
     */
    public void addMetrics(Metric metric) {
        this.metrics.add(metric);
        this.metricsNameMap.put(metric.getName(), metric);
    }

    /**
     * 获取sub group by name。
     *
     * @param name name参数。
     * @return 处理结果。
     */
    public MetricsGroup getSubGroupByName(String name) {

        for (MetricsGroup grp : this.subGroups)
            if (grp.getGroupName().equals(name))
                return grp;


        return null;
    }

    /**
     * If the content ever changed, need re-calculate it first
     *
     * @return
     */
    @Getter
    private int length;

    /**
     * 执行calculate length操作。
     */
    public void calculateLength() {
        int l = 0;

        for (Metric m : this.metrics)
            l += m.getDataType().getLength();

        this.length = l;
    }

    /**
     * 添加sub groups。
     *
     * @param subGroup sub group参数。
     */
    public void addSubGroups(MetricsGroup subGroup) {

        if (subGroup == null)
            return;

        subGroup.setParentGroup(this);
        this.subGroups.add(subGroup);
    }

    /**
     * Table name of the metrics storage
     *
     * @return
     */
    public String getSinkTableName() {

        if (this.isStoreInCommonTable())
            if (this.targetTable != null)
                return this.targetTable;
            else return "METRIC_GENERIC";

        else if (this.udmFlagged)
            return "UDM_" + this.groupName.toUpperCase();

        return (this.dbType + "_" + this.groupName).toUpperCase();
    }

    /**
     * 获取metric full name。
     *
     * @param metricName metric name参数。
     * @return 处理结果。
     */
    public String getMetricFullName(String metricName) {

        if (!this.metricsNameMap.containsKey(metricName))
            return null;

        String prefix;

        if (this.udmFlagged)
            prefix = "UDM." + this.groupName;//udm
        else if (this.parentGroup == null)
            prefix = this.groupName + ".";//top level
        else
            prefix = this.parentGroup.getGroupName() + "." + this.groupName;

        return prefix + "." + metricName;
    }

    /**
     * 获取metric by name。
     *
     * @param metricName metric name参数。
     * @return 处理结果。
     */
    public Metric getMetricByName(String metricName) {
        return this.metricsNameMap.get(metricName);
    }
}
