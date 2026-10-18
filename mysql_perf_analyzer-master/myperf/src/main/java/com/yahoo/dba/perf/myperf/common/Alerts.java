/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * For now, it holds alerts for web notification purpose.
 *
 * @author xrao
 */
public class Alerts implements Serializable {
    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 1L;
    /**
     * 定义alert capacity常量。
     */
    public static final int ALERT_CAPACITY = 100;//hold 100 alerts

    /**
     * 保存entries集合。
     */
    private List<AlertEntry> entries = null;

    /**
     * 创建Alerts对象。
     */
    public Alerts() {
        this.entries = new ArrayList<>(ALERT_CAPACITY);
    }

    /**
     * Add a new entry. If list full, remove first one
     *
     * @param entry
     */
    synchronized public void addAlert(AlertEntry entry) {

        if (this.entries.size() >= ALERT_CAPACITY)
            this.entries.remove(0);//remove first one

        this.entries.add(entry);
    }

    /**
     * Retrieve a list of alerts later than given ts, up to max
     *
     * @param ts
     * @return
     */
    synchronized public List<AlertEntry> getEntries(long ts, int max) {
        List<AlertEntry> resList = new ArrayList<>();
        int cnt = 0;

        for (int i = this.entries.size() - 1; i >= 0; i--) {
            AlertEntry e = this.entries.get(i);

            if (e.getTs() > ts) {
                resList.add(e);
                cnt++;

                if (max > 0 && cnt >= max)
                    break;
            }
        }

        return resList;
    }
}
