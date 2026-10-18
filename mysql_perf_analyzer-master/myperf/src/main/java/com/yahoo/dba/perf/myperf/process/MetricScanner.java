/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.process;

import com.yahoo.dba.perf.myperf.common.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 表示metric scanner相关的类型。
 */
@Slf4j
public class MetricScanner {
    /**
     * 保存framework context信息。
     */
    @Getter
    @Setter
    private MyPerfContext frameworkContext;
    /**
     * 保存app user信息。
     */
    @Getter
    @Setter
    private AppUser appUser;//run as user
    /**
     * 保存thread count信息。
     */
    @Getter
    private int threadCount = 4;
    /**
     * 保存record count信息。
     */
    @Getter
    @Setter
    private int recordCount = 840;
    /**
     * 保存buffer信息。
     */
    @Getter
    @Setter
    private HashMap<String, Map<String, MetricsBuffer>> buffer;
    /**
     * 保存sdf信息。
     */
    protected SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");

    //private Map<String, List<MetricsGroup>> builtinMetrics = new HashMap<String, List<MetricsGroup>>();

    /**
     * 创建MetricScanner对象。
     */
    public MetricScanner() {
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /**
     * 执行scan auto操作。
     */
    public void scanAuto() {
        log.info(new Date() + ": Starting auto metrics scan ...");
        Date startDate = null;
        Date endDate;

        int snap_id = -1;
        try {
            startDate = new Date();
            snap_id = this.frameworkContext.getMetricDb().getNextSnapshotId(Long.parseLong(sdf.format(startDate)));

            if (snap_id >= 0)
                scan(snap_id);
            else
                log.error("Error to generate valid snap_id, got " + snap_id);

        } catch (Exception ex) {
            log.error("Exception", ex);
        }

        this.frameworkContext.getMetricDb().updateSnapCompleteTime(snap_id, Long.parseLong(sdf.format(new Date())));
        frameworkContext.getInstanceStatesManager().saveStatus();
        endDate = new Date();
        log.info(new Date() + ": Auto metrics scan done and status saved. Total Time in ms: " + (endDate.getTime() - startDate.getTime()));
    }

    //setup metrics buffer if need

    /**
     * 检查and setup metrics buffer。
     *
     * @param dbinfo dbinfo参数。
     */
    private void checkAndSetupMetricsBuffer(DBInstanceInfo dbinfo) {
        //will set up UDM buffer when need
        if (!buffer.containsKey(dbinfo.getDbGroupName() + ":" + dbinfo.getHostName())) {
            Map<String, MetricsBuffer> ms = new HashMap<>();
            buffer.put(dbinfo.getDbGroupName() + ":" + dbinfo.getHostName(), ms);
        }

        String[] mgNames = this.frameworkContext.getMetricsDef().getGroupNames();

        for (String mgName : mgNames) {
            MetricsGroup mg = this.frameworkContext.getMetricsDef().getGroupByName(mgName);

            if (mg == null)
                continue;

            if (buffer.containsKey(dbinfo.getDbGroupName() + ":" + dbinfo.getHostName())) {
                Map<String, MetricsBuffer> bufs = buffer.get(dbinfo.getDbGroupName() + ":" + dbinfo.getHostName());

                if (mg.getSubGroups() != null && mg.getSubGroups().size() > 0) { //sub groups

                    for (MetricsGroup subGrp : mg.getSubGroups()) {
                        String subGrpName = mg.getGroupName() + "." + subGrp.getGroupName();

                        if (!bufs.containsKey(subGrpName)) {
                            //MetricsBuffer buf = new MetricsBuffer(mg, this.recordCount+20);
                            MetricsBuffer buf = new MetricsBuffer(subGrp);//no more cache
                            buf.setDbid(dbinfo.getDbid());
                            log.info("add buffer for " + dbinfo + ", " + subGrpName);
                            bufs.put(subGrpName, buf);
                        }
                    }

                } else {//top level

                    if (!bufs.containsKey(mg.getGroupName())) {
                        //MetricsBuffer buf = new MetricsBuffer(mg, this.recordCount+20);
                        MetricsBuffer buf = new MetricsBuffer(mg);//no more cache
                        buf.setDbid(dbinfo.getDbid());
                        log.warn("add buffer for " + dbinfo + ", " + mg.getGroupName());
                        bufs.put(mg.getGroupName(), buf);
                    }
                }
            }
        }
    }

    /**
     * 保存metrics scanner runners信息。
     */
    private MetricScannerRunner[] metricsScannerRunners = null;

    /**
     * 执行scan操作。
     *
     * @param snap_id snap id参数。
     */
    public void scan(int snap_id) {
        Set<String> clusternames = frameworkContext.getDbInfoManager().getMyDatabases(appUser.getName(), false).getMyDbList();
        log.info("Start scan metrics");

        if (this.buffer == null) {
            log.error("Data buffer was not found. Scan cannot continue.");
            return;
        }

        LinkedBlockingQueue<DBInstanceInfo> dbqueue = new LinkedBlockingQueue<>();

        for (String cl : clusternames) {
            DBCredential cred = DBUtils.findDBCredential(frameworkContext, cl, appUser);

            if (cred == null) {
                log.info("No credential for group " + cl + ", skip it");
                continue;//log the error
            }

            DBGroupInfo cls = frameworkContext.getDbInfoManager().findGroup(cl);

            if (cls == null) {
                log.info("Group " + cl + " might have been deleted.");
                continue;
            }

            for (DBInstanceInfo dbinfo : cls.getInstances()) {
                checkAndSetupMetricsBuffer(dbinfo);
                dbqueue.add(dbinfo);
            }
        }

        int mythreadcnt = this.threadCount;

        if (dbqueue.size() < mythreadcnt)
            mythreadcnt = dbqueue.size();

        Thread th[] = new Thread[mythreadcnt];
        metricsScannerRunners = new MetricScannerRunner[mythreadcnt];

        for (int i = 0; i < mythreadcnt; i++) {
            MetricScannerRunner runner = new MetricScannerRunner(frameworkContext,
                    dbqueue,
                    appUser,
                    snap_id);
            runner.setBuffer(buffer);
//			runner.setBuiltinMetrics(builtinMetrics);
            th[i] = new Thread(runner);
            metricsScannerRunners[i] = runner;
            th[i].setName("MetricScannerRunner - " + i);
            th[i].start();
        }

        for (Thread thread : th)
            try {
                thread.join();
            } catch (Exception ex) {
            }

        log.info("Done gather metrics");
        this.frameworkContext.getAutoScanner().getMetricDb().flush();//notify persistent store
    }

    /**
     * 执行shutdown操作。
     */
    public void shutdown() {

        if (this.metricsScannerRunners != null)
            for (MetricScannerRunner runner : this.metricsScannerRunners)
                if (runner != null)
                    runner.shutdown();


    }

    /**
     * 设置thread count。
     *
     * @param threadCount thread count参数。
     */
    public void setThreadCount(int threadCount) {

        if (threadCount <= 0)
            threadCount = 1;
        else
            this.threadCount = threadCount;
    }

}