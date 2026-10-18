/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * Place holder for alert information
 *
 * @author xrao
 */
public class AlertEntry implements Serializable {
    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 保存ts信息。
     */
    @Getter
    @Setter
    private long ts; //timestamp
    /**
     * 保存alert reason信息。
     */
    @Getter
    @Setter
    private String alertReason;//the type of alert
    /**
     * 保存alert value信息。
     */
    @Getter
    @Setter
    private String alertValue;//the value triggered alert
    /**
     * 保存db group信息。
     */
    @Getter
    @Setter
    private String dbGroup;//database group
    /**
     * 保存db host信息。
     */
    @Getter
    @Setter
    private String dbHost;//database server host

    /**
     * 创建AlertEntry对象。
     */
    public AlertEntry() {

    }

    /**
     * 创建AlertEntry对象。
     *
     * @param ts          ts参数。
     * @param alertReason alert reason参数。
     * @param alertValue  alert value参数。
     * @param dbGroup     db group参数。
     * @param dbHost      db host参数。
     */
    public AlertEntry(long ts, String alertReason, String alertValue, String dbGroup, String dbHost) {
        this.ts = ts;
        this.alertReason = alertReason;
        this.alertValue = alertValue;
        this.dbGroup = dbGroup;
        this.dbHost = dbHost;
    }

    /**
     * 获取alert time。
     *
     * @return 处理结果。
     */
    public String getAlertTime() {
        return CommonUtils.formatTimeFromTimestamp(ts, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 转换为string。
     *
     * @return 处理结果。
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ALERT: ").append(this.alertReason);

        if (this.alertValue != null && !this.alertValue.isEmpty())
            sb.append(", ").append(this.alertValue);

        sb.append(", ").append(this.dbGroup).append(", ").append(this.dbHost);
        sb.append(", TIME: ").append(this.getAlertTime());
        return sb.toString();
    }


}
