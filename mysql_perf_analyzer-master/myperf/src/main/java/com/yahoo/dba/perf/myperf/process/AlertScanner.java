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
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
public class AlertScanner {
    @Getter
    @Setter
    private MyPerfContext frameworkContext;
    @Getter
    @Setter
    private AppUser appUser;//run as user
    @Getter
    private int threadCount = 4;
    private HashMap<String, Map<String, MetricsBuffer>> buffer;
    protected SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");


    public AlertScanner() {
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public void scanAuto() {
        log.info(new Date() + ": Starting auto alert scan ...");
        Date startDate = null;
        Date endDate = null;

        try {
            startDate = new Date();
            scan();
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        endDate = new Date();
        log.info(new Date() + ": Auto alert scan done and status saved. Total Time in ms: " + (endDate.getTime() - startDate.getTime()));
    }

    public void scan() {
        Set<String> clusternames = frameworkContext.getDbInfoManager().getMyDatabases(appUser.getName(), false).getMyDbList();
        log.info("Start scan alerts");
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
                dbqueue.add(dbinfo);
            }
        }

        int mythreadcnt = this.threadCount;
        if (dbqueue.size() < mythreadcnt) mythreadcnt = dbqueue.size();
        Thread th[] = new Thread[mythreadcnt];
        for (int i = 0; i < mythreadcnt; i++) {
            AlertScannerRunner runner = new
                    AlertScannerRunner(frameworkContext,
                    dbqueue,
                    appUser);
            th[i] = new Thread(runner);
            th[i].setName("AlertScannerRunner - " + i);
            th[i].start();
        }
        for (int i = 0; i < th.length; i++)
            try {
                th[i].join();
            } catch (Exception ex) {
            }

        log.info("Done alert scanner");
        this.frameworkContext.getAutoScanner().getMetricDb().flush();//notify persistent store
    }

    public void setThreadCount(int threadCount) {
        if (threadCount <= 0) threadCount = 1;
        else this.threadCount = threadCount;
    }
}