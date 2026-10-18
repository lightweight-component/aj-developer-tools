/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.process;

import com.yahoo.dba.perf.myperf.common.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Handle metrics retention
 */
@Slf4j
@RequiredArgsConstructor
public class MetricsRetentionTask implements Runnable {
    private final MyPerfContext context;

    /**
     * renentionDays will be ignored if dbidToPurge is set
     */
    private final int renentionDays;

    /**
     * use to start an onefold job to purge data for a specific db, set to -1 otherwise
     */
    private final int[] dbidToPurge; //set as one time job, reuse code

    @Override
    public void run() {
        Thread.currentThread().setName("MetricsRetentionTask");
        log.info("Starting metrics purge job");

        if (this.context.getMetricDb() == null) {
            log.info("MetricsDB has yet to set.");
            return;
        }

        //get all dbids
        List<Integer> ids = new ArrayList<>();
        if (this.dbidToPurge == null) {
            for (Map.Entry<String, DBGroupInfo> e : context.getDbInfoManager().getClusters().entrySet()) {
                DBGroupInfo g = e.getValue();

                for (DBInstanceInfo i : g.getInstances())
                    ids.add(i.getDbid());
            }
        } else {
            for (int id : this.dbidToPurge)
                ids.add(id);
        }

        Calendar c = Calendar.getInstance();
        c.add(Calendar.DATE, -this.renentionDays);
        Date dt = c.getTime();

        //now get current timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        long endDate = Long.parseLong(sdf.format(dt));
        List<MetricsGroup> mgs = new ArrayList<>();

        String[] groupNames = this.context.getMetricsDef().getGroupNames();

        for (String grpName : groupNames) {
            MetricsGroup group = this.context.getMetricsDef().getGroupByName(grpName);
            if (group == null)
                continue; //not supposed to be so

            if (group.getSubGroups().size() == 0) //no subgroup, add self
                mgs.add(group);
             else {
                for (MetricsGroup g : group.getSubGroups())
                    mgs.add(g);
            }
        }

        for (Map.Entry<String, UserDefinedMetrics> entry : this.context.getMetricsDef().getUdmManager().getUdms().entrySet()) {
            MetricsGroup group = entry.getValue().getMetricsGroup();

            if (group == null)
                continue; //not supposed to be so

            mgs.add(group);
        }

        for (int dbid : ids) {
            log.info("Check and purge db: " + dbid);

            for (MetricsGroup g : mgs) {
                if (this.dbidToPurge != null)
                    this.context.getMetricDb().purgeAll(g.getSinkTableName(), dbid);
                else
                    this.context.getMetricDb().purge(g.getSinkTableName(), dbid, endDate);
            }
        }

        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMdd");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        purgeAlertReports(Integer.parseInt(sdf2.format(dt)));
        log.info("Ended metrics purge job");

        if (this.dbidToPurge == null)
            this.context.getMetricDb().purgeAlerts(endDate);

        //for now, we only do it once a day.
        //TODO keep consistency with DB add/update/delete
        log.info("Retention job done.");
    }

    /**
     * @param lastPurgeDate The last date the report will be purged, in YYYYMMDD format
     */
    private void purgeAlertReports(int lastPurgeDate) {
        try {
            //also remove old alert reports
            //report root
            File rootPath = this.context.getAlertRootPath();
            if (!rootPath.exists() && !rootPath.isDirectory()) {
                log.warn("Failed to find alert root path: " + rootPath.getPath());
                return;
            }

            log.warn("Start to purge alert reports: " + rootPath.getPath() + ", before " + lastPurgeDate);

            File[] dirByDates = rootPath.listFiles();

            for (File dir : dirByDates) {
                String name = dir.getName();
                log.info("Trying to delete report for date: " + name);

                if (name.indexOf(".") > 0)
                    name = name.substring(0, name.indexOf("."));

                try {
                    int nameDate = Integer.parseInt(name);

                    if (nameDate < lastPurgeDate) {
                        if (dir.isDirectory()) {
                            for (File ar : dir.listFiles()) {
                                //don't expect subdir
                                if (ar.isFile())
                                    ar.delete();
                            }
                        }

                        dir.delete();
                    }
                } catch (Exception ex) {
                    log.warn("Failed to delete report for date: " + name);
                }
            }
        } catch (Throwable th) {
            log.warn("Failed to purge report data: " + th.getMessage());
        }
    }
}
