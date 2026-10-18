/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.Map;

/**
 * The default compartor is sorted by total_time_sec
 *
 * @author xrao
 */
public class ProcessListEntryAggregate implements Serializable, Comparable<ProcessListEntryAggregate> {
    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 定义hexes常量。
     */
    static final char HEXES[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    /**
     * 保存sql信息。
     */
    @Getter
    private String sql;//that will be the key
    /**
     * 保存count信息。
     */
    @Getter
    private int count = 0;
    /**
     * 保存total time sec信息。
     */
    @Getter
    private int total_time_sec = 0;//at this moment, sec only
    /**
     * 保存min time sec信息。
     */
    @Getter
    private int min_time_sec = -1;//minimum time recorded -1 manes not updated yet
    /**
     * 保存max time sec信息。
     */
    @Getter
    private int max_time_sec = 0;//max time recorded

    /**
     * 保存checksum信息。
     */
    @Getter
    @Setter
    private String checksum;

    /**
     * 表示sort by count相关的类型。
     */
    public static class SortByCount implements Comparator<ProcessListEntryAggregate> {

        /**
         * 执行compare操作。
         *
         * @param obj1 obj1参数。
         * @param obj2 obj2参数。
         * @return 处理结果。
         */
        @Override
        public int compare(ProcessListEntryAggregate obj1,
                           ProcessListEntryAggregate obj2) {

            if (obj1 == null && obj2 == null)
                return 0;
            else if (obj1 == null) return 1;
            else if (obj2 == null) return -1;

            return obj2.getCount() - obj1.getCount();//reverse order
        }

    }

    /**
     * 创建ProcessListEntryAggregate对象。
     *
     * @param sql sql参数。
     */
    public ProcessListEntryAggregate(String sql) {
        this.sql = sql;
    }

    /**
     * 获取average。
     *
     * @return 处理结果。
     */
    public float getAverage() {

        if (this.count == 0)
            return 0;
        else
            return (float) this.total_time_sec / (float) this.count;
    }

    /**
     * Record one execution instance time. Later we might want to add more metrics for example, rows_read, etc
     *
     * @param time_sec
     */
    public void record(int time_sec) {
        this.count++;
        this.total_time_sec += time_sec;

        if (this.min_time_sec < 0)
            this.min_time_sec = time_sec;
        else
            this.min_time_sec = Math.min(this.min_time_sec, time_sec);

        this.max_time_sec = Math.max(this.max_time_sec, time_sec);
    }

    /**
     * 比较to。
     *
     * @param obj obj参数。
     * @return 处理结果。
     */
    @Override
    public int compareTo(ProcessListEntryAggregate obj) {

        if (obj == null)
            return -1;

        return obj.getTotal_time_sec() - this.getTotal_time_sec();
    }

    /**
     * 更新data map。
     *
     * @param m        m参数。
     * @param sql      sql参数。
     * @param time_sec time sec参数。
     */
    public static void updateDataMap(Map<String, ProcessListEntryAggregate> m, String sql, int time_sec) {

        if (m == null)
            return;

        if (m.containsKey(sql))
            m.get(sql).record(time_sec);
        else {
            ProcessListEntryAggregate e = new ProcessListEntryAggregate(sql);
            e.setChecksum(generateDigest(sql));
            e.record(time_sec);
            m.put(sql, e);
        }
    }

    /**
     * Generate a digest for each query string
     *
     * @param str
     * @return
     */
    public static String generateDigest(String str) {

        if (str == null || str.isEmpty())
            return "00000000";

        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            md.update(str.getBytes());
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();

            for (byte b : digest)
                sb.append(HEXES[(b & 0xF0) >> 4])
                        .append(HEXES[(b & 0x0F)]);

            return sb.toString();
        } catch (Exception ex) {

        }
        return "00000000";
    }

}
