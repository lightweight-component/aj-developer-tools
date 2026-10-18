/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

/**
 * Place holder for alert information
 *
 * @author xrao
 */
public class AlertEntry implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    @Getter
    @Setter
    private long ts; //timestamp
    @Getter
    @Setter
    private String alertReason;//the type of alert
    @Getter
    @Setter
    private String alertValue;//the value triggered alert
    @Getter
    @Setter
    private String dbGroup;//database group
    @Getter
    @Setter
    private String dbHost;//database server host

    public AlertEntry() {

    }

    public AlertEntry(long ts, String alertReason, String alertValue, String dbGroup, String dbHost) {
        this.ts = ts;
        this.alertReason = alertReason;
        this.alertValue = alertValue;
        this.dbGroup = dbGroup;
        this.dbHost = dbHost;
    }

    public String getAlertTime() {
        return CommonUtils.formatTimeFromTimestamp(ts, "yyyy-MM-dd HH:mm:ss");
    }

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
