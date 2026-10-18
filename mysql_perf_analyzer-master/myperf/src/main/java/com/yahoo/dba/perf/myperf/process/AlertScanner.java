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
 * 表示alert scanner相关的类型。
 */
@Slf4j
public class AlertScanner {
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
     * 保存buffer信息。
     */
    private HashMap<String, Map<String, MetricsBuffer>> buffer;
    /**
     * 保存sdf信息。
     */
    protected SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");

    /**
     * 创建AlertScanner对象。
     */
    public AlertScanner() {
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /**
     * 执行scan auto操作。
     */
    public void scanAuto() {
        log.info(new Date() + ": Starting auto alert scan ...");
        Date startDate = null;
        Date endDate;

        try {
            startDate = new Date();
            scan();
        } catch (Exception ex) {
            log.error("Exception", ex);
        }

        endDate = new Date();
        log.info(new Date() + ": Auto alert scan done and status saved. Total Time in ms: " + (endDate.getTime() - startDate.getTime()));
    }

    /**
     * 执行scan操作。
     */
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

            for (DBInstanceInfo dbinfo : cls.getInstances())
                dbqueue.add(dbinfo);
        }

        int mythreadcnt = this.threadCount;

        if (dbqueue.size() < mythreadcnt)
            mythreadcnt = dbqueue.size();

        Thread th[] = new Thread[mythreadcnt];

        for (int i = 0; i < mythreadcnt; i++) {
            AlertScannerRunner runner = new AlertScannerRunner(frameworkContext, dbqueue, appUser);
            th[i] = new Thread(runner);
            th[i].setName("AlertScannerRunner - " + i);
            th[i].start();
        }

        for (Thread thread : th)
            try {
                thread.join();
            } catch (Exception ex) {
            }

        log.info("Done alert scanner");
        this.frameworkContext.getAutoScanner().getMetricDb().flush();//notify persistent store
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