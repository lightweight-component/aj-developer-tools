/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.process;

import lombok.Getter;
import lombok.Setter;

import lombok.extern.slf4j.Slf4j;

import com.yahoo.dba.perf.myperf.common.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class MetricScanner {
    @Getter
    @Setter
    private MyPerfContext frameworkContext;
    @Getter
    @Setter
    private AppUser appUser;//run as user
    @Getter
    private int threadCount = 4;
    @Getter
    @Setter
    private int recordCount = 840;
    @Getter
    @Setter
    private HashMap<String, Map<String, MetricsBuffer>> buffer;
    protected java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMddHHmmss");

    //private Map<String, List<MetricsGroup>> builtinMetrics = new HashMap<String, List<MetricsGroup>>();

    public MetricScanner() {
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public void scanAuto() {
        log.info(new java.util.Date() + ": Starting auto metrics scan ...");
        java.util.Date startDate = null;
        java.util.Date endDate = null;

        int snap_id = -1;
        try {
            startDate = new java.util.Date();
            snap_id = this.frameworkContext.getMetricDb().getNextSnapshotId(Long.parseLong(sdf.format(startDate)));
            if (snap_id >= 0)
                scan(snap_id);
            else {
                log.error("Error to generate valid snap_id, got " + snap_id);
            }
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        this.frameworkContext.getMetricDb().updateSnapCompleteTime(snap_id, Long.parseLong(sdf.format(new java.util.Date())));
        frameworkContext.getInstanceStatesManager().saveStatus();
        endDate = new java.util.Date();
        log.info(new java.util.Date() + ": Auto metrics scan done and status saved. Total Time in ms: " + (endDate.getTime() - startDate.getTime()));
    }

    //setup metrics buffer if need
    private void checkAndSetupMetricsBuffer(DBInstanceInfo dbinfo) {
        //will setup UDM buffer when need
        if (!buffer.containsKey(dbinfo.getDbGroupName() + ":" + dbinfo.getHostName())) {
            Map<String, MetricsBuffer> ms = new HashMap<String, MetricsBuffer>();
            buffer.put(dbinfo.getDbGroupName() + ":" + dbinfo.getHostName(), ms);
        }
        String[] mgNames = this.frameworkContext.getMetricsDef().getGroupNames();
        for (String mgName : mgNames) {
            MetricsGroup mg = this.frameworkContext.getMetricsDef().getGroupByName(mgName);
            if (mg == null) continue;

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
                } else //top level
                {
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

    private MetricScannerRunner[] metricsScannerRunners = null;

    public void scan(int snap_id) {
        Set<String> clusternames = frameworkContext.getDbInfoManager().getMyDatabases(appUser.getName(), false).getMyDbList();
        log.info("Start scan metrics");
        if (this.buffer == null) {
            log.error("Data buffer was not found. Scan cannot continue.");
            return;
        }
        LinkedBlockingQueue<DBInstanceInfo> dbqueue = new LinkedBlockingQueue<DBInstanceInfo>();
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
        if (dbqueue.size() < mythreadcnt) mythreadcnt = dbqueue.size();
        Thread th[] = new Thread[mythreadcnt];
        metricsScannerRunners = new MetricScannerRunner[mythreadcnt];
        for (int i = 0; i < mythreadcnt; i++) {
            MetricScannerRunner runner = new
                    MetricScannerRunner(frameworkContext,
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
        for (int i = 0; i < th.length; i++)
            try {
                th[i].join();
            } catch (Exception ex) {
            }

        log.info("Done gather metrics");
        this.frameworkContext.getAutoScanner().getMetricDb().flush();//notify persistent store
    }

    public void shutdown() {
        if (this.metricsScannerRunners != null) {
            for (MetricScannerRunner runner : this.metricsScannerRunners) {
                if (runner != null) runner.shutdown();
            }
        }
    }

    public void setThreadCount(int threadCount) {
        if (threadCount <= 0) threadCount = 1;
        else this.threadCount = threadCount;
    }

}