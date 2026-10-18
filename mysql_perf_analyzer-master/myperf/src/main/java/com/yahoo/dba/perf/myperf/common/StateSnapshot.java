/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 表示state snapshot相关的类型。
 */
public class StateSnapshot implements Serializable {
    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 1L;
    /**
     * 保存timestamp信息。
     */
    @Getter
    @Setter
    private long timestamp = -1L;//snap timestamp, -1L means no data yet
    //metrics, -1 is NA - not available
    /**
     * 保存usercputime信息。
     */
    @Getter
    @Setter
    private long usercputime = -1L;
    /**
     * 保存syscputime信息。
     */
    @Getter
    @Setter
    private long syscputime = -1L;
    /**
     * 保存softirqtime信息。
     */
    @Getter
    @Setter
    private long softirqtime = -1L;
    /**
     * 保存iotime信息。
     */
    @Getter
    @Setter
    private long iotime = -1L;
    /**
     * 保存available mem信息。
     */
    @Getter
    @Setter
    private long availableMem = -1L;
    /**
     * 保存load average信息。
     */
    @Getter
    @Setter
    private float loadAverage = -1.0f;
    /**
     * 保存threads信息。
     */
    @Getter
    @Setter
    private int threads = 1; //total threads
    /**
     * 保存active threads信息。
     */
    @Getter
    @Setter
    private int activeThreads = 1; //active threads
    /**
     * 保存connections信息。
     */
    @Getter
    @Setter
    private long connections = -1L;
    /**
     * 保存aborted connects clients信息。
     */
    @Getter
    @Setter
    private long abortedConnectsClients = -1L;//sum of aborted_connects and aborted_clients
    /**
     * 保存slow query count信息。
     */
    @Getter
    @Setter
    private long slowQueryCount = -1L;
    /**
     * 保存totalcputime信息。
     */
    @Getter
    @Setter
    private long totalcputime = -1L;//cpu time, wait time plus idle time
    /**
     * 保存repl lag信息。
     */
    @Getter
    @Setter
    private int replLag = 0;//replication lag in seconds
    /**
     * 保存repl io信息。
     */
    @Getter
    @Setter
    private int replIo = 1;//replication io thread status, default to running
    /**
     * 保存repl sql信息。
     */
    @Getter
    @Setter
    private int replSql = 1; //replication sql thread status, default to running
    /**
     * 保存queries信息。
     */
    @Getter
    @Setter
    private long queries = 0L;
    /**
     * 保存questions信息。
     */
    @Getter
    @Setter
    private long questions = 0L;
    /**
     * 保存deadlocks信息。
     */
    @Getter
    @Setter
    private long deadlocks = 0L;
    /**
     * 保存swapout信息。
     */
    @Getter
    @Setter
    private long swapout = -1L; //memory swap out, use ssRawSwapOut
    /**
     * 保存max conn error信息。
     */
    @Getter
    @Setter
    private long max_conn_error = -1L;//-1 means not recorded yet

    /**
     * 保存uptime信息。
     */
    @Getter
    @Setter
    private long uptime = 0L;//need it to calculate diff if restart

    //NOTE: when add new fields, make sure to update function copy()

    //for user defined alertsk, name is the alertName
    /**
     * 保存metrics map集合。
     */
    @Getter
    private Map<String, Float> metricsMap;

    /**
     * 创建StateSnapshot对象。
     */
    public StateSnapshot() {
        metricsMap = new HashMap<>();
    }

    /**
     * Make a copy
     *
     * @return
     */
    public StateSnapshot copy() {
        StateSnapshot ss = new StateSnapshot();
        ss.setAvailableMem(this.availableMem);
        ss.setConnections(this.connections);
        ss.setIotime(iotime);
        ss.setLoadAverage(loadAverage);
        ss.setSyscputime(syscputime);
        ss.setSoftirqtime(softirqtime);
        ss.setThreads(threads);
        ss.setTimestamp(timestamp);
        ss.setUsercputime(usercputime);
        ss.setSlowQueryCount(slowQueryCount);
        ss.setActiveThreads(activeThreads);
        ss.setTotalcputime(totalcputime);
        ss.setReplLag(replLag);
        ss.setQueries(queries);
        ss.setQuestions(questions);
        ss.setAbortedConnectsClients(abortedConnectsClients);
        ss.setReplIo(this.replIo);
        ss.setReplSql(this.replSql);
        ss.setDeadlocks(deadlocks);
        ss.setSwapout(this.swapout);
        ss.setUptime(uptime);
        ss.setMax_conn_error(max_conn_error);

        if (this.metricsMap != null)
            for (Map.Entry<String, Float> e : this.metricsMap.entrySet())
                ss.addMetric(e.getKey(), e.getValue());

        return ss;
    }

    /**
     * 添加metric。
     *
     * @param name name参数。
     * @param val  val参数。
     */
    public void addMetric(String name, Float val) {

        if (this.metricsMap == null)
            this.metricsMap = new HashMap<>();

        this.metricsMap.put(name, val);
    }

    /**
     * Extract data from snmp scan
     *
     * @param snmpMap
     */
    public void recordSnmpStats(Map<String, String> snmpMap) {

        if (snmpMap == null)
            return;

        setSyscputime(CommonUtils.getMapValueLong(snmpMap, "ssCpuRawSystem", -1L));
        setUsercputime(CommonUtils.getMapValueLong(snmpMap, "ssCpuRawUser", -1L));
        this.setSoftirqtime(CommonUtils.getMapValueLong(snmpMap, "ssCpuRawSoftIRQ", -1L));
        setAvailableMem(CommonUtils.getMapValueLong(snmpMap, "memAvailReal", -1L) + CommonUtils.getMapValueLong(snmpMap, "memCached", -1L));
        setIotime(CommonUtils.getMapValueLong(snmpMap, "ssCpuRawWait", -1L));
        setLoadAverage(CommonUtils.getMapValueFloat(snmpMap, "laLoad5m", -1.0f));
        setTotalcputime(CommonUtils.getMapValueLong(snmpMap, "ssCpuRawSystem", -1L)
                + CommonUtils.getMapValueLong(snmpMap, "ssCpuRawUser", -1L)
                + CommonUtils.getMapValueLong(snmpMap, "ssCpuRawWait", -1L)
                + CommonUtils.getMapValueLong(snmpMap, "ssCpuRawIdle", -1L)
                + CommonUtils.getMapValueLong(snmpMap, "ssCpuRawNice", -1L)
                + CommonUtils.getMapValueLong(snmpMap, "ssCpuRawSoftIRQ", -1L));

        if (this.getTotalcputime() < 0L)
            this.setTotalcputime(-1L);

        this.setSwapout(CommonUtils.getMapValueLong(snmpMap, "ssRawSwapOut", -1L));

    }

    /**
     * Extract data from global status
     *
     * @param kvPairs
     */
    public void recordSnapFromMySQLStatus(Map<String, String> kvPairs) {

        if (kvPairs == null)
            return;

        setSlowQueryCount(CommonUtils.getMapValueLong(kvPairs, "SLOW_QUERIES", 0L));
        setConnections(CommonUtils.getMapValueLong(kvPairs, "CONNECTIONS", 0L));
        setQueries(CommonUtils.getMapValueLong(kvPairs, "QUERIES", 0L));
        setThreads((int) CommonUtils.getMapValueLong(kvPairs, "THREADS_CONNECTED", 0L));
        setAbortedConnectsClients(CommonUtils.getMapValueLong(kvPairs, "ABORTED_CONNECTS", 0L));
        setActiveThreads((int) CommonUtils.getMapValueLong(kvPairs, "THREADS_RUNNING", 0L));
        setDeadlocks(CommonUtils.getMapValueLong(kvPairs, "INNODB_DEADLOCKS", 0L));
        setUptime(CommonUtils.getMapValueLong(kvPairs, "UPTIME", 0L));
        setMax_conn_error(CommonUtils.getMapValueLong(kvPairs, "CONNECTION_ERRORS_MAX_CONNECTIONS", 0L));
    }

    /**
     * 执行record repl操作。
     *
     * @param resMap res map参数。
     */
    public void recordRepl(Map<String, String> resMap) {
        try {
            setReplLag(Integer.parseInt(resMap.get("Seconds_Behind_Master")));
            setReplIo(Integer.parseInt(resMap.get("Slave_IO_Running")));
            setReplSql(Integer.parseInt(resMap.get("Slave_SQL_Running")));

        } catch (Exception iex) {

        }

    }

}
