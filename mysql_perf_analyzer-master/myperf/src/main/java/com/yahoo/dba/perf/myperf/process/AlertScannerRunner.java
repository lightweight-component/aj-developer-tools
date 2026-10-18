/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.process;

import com.yahoo.dba.perf.myperf.common.*;
import com.yahoo.dba.perf.myperf.db.UserDBConnections;
import com.yahoo.dba.perf.myperf.process.model.ScanData;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 表示alert scanner runner相关的类型。
 */
@Slf4j
public class AlertScannerRunner implements Runnable {
    /**
     * 保存framework context信息。
     */
    private final MyPerfContext frameworkContext;

    //note those objects are shared, so we need handle synchronization here
    /**
     * 保存dbqueue信息。
     */
    private final LinkedBlockingQueue<DBInstanceInfo> dbqueue;//distribute job
    /**
     * 保存app user信息。
     */
    private final AppUser appUser;
    /**
     * 保存conns信息。
     */
    private UserDBConnections conns;

    /**
     * 保存sdf信息。
     */
    protected SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");

    /**
     * 创建AlertScannerRunner对象。
     *
     * @param frameworkContext framework context参数。
     * @param dbqueue          dbqueue参数。
     * @param appUser          app user参数。
     */
    public AlertScannerRunner(MyPerfContext frameworkContext, LinkedBlockingQueue<DBInstanceInfo> dbqueue, AppUser appUser) {
        this.frameworkContext = frameworkContext;
        this.dbqueue = dbqueue;
        this.appUser = appUser;
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /**
     * 执行run操作。
     */
    public void run() {
        scan();
    }

    /**
     * 执行scan操作。
     */
    private void scan() {
        conns = new UserDBConnections();
        conns.setAppUser(appUser.getName());
        conns.setFrameworkContext(frameworkContext);
        int count = 0;

        while (!dbqueue.isEmpty()) {
            try {
                DBInstanceInfo db = this.dbqueue.poll(1, TimeUnit.SECONDS);
                count++;

                if (db == null || !db.isAlertEnabled())
                    continue;

                long startTime = System.currentTimeMillis();
                scanHost(db);
                //this.frameworkContext.getInstanceStatesManager().getStates(db.getDbid()).setLastScanTime(System.currentTimeMillis() - startTime);
            } catch (Exception ex) {
                log.warn("Exception: scan loop", ex);
            }
        }

        log.info(Thread.currentThread() + " done scan alerts: " + count + " servers.");
    }


    /**
     * 获取alert threshold。
     *
     * @param dbinfo dbinfo参数。
     * @return 处理结果。
     */
    private Map<String, Float> getAlertThreshold(DBInstanceInfo dbinfo) {
        Map<String, Float> thresholds = new HashMap<>(AlertSettings.COMMON_ALERTS.length);

        for (String alertType : AlertSettings.COMMON_ALERTS)
            thresholds.put(alertType, this.frameworkContext
                    .getAlertSettings()
                    .getAlertThreshold(dbinfo.getDbGroupName(), dbinfo.getHostName(), alertType));

        return thresholds;
    }

    /**
     * 执行try connect操作。
     *
     * @param scanData scan data参数。
     * @return 是否满足条件。
     */
    private boolean tryConnect(ScanData scanData) {
        DBCredential cred = DBUtils.findDBCredential(frameworkContext, scanData.dbinfo.getDbGroupName(), appUser);

        if (cred == null) {
            log.info("No credential for cluster " + scanData.dbinfo.getDbGroupName() + ", skip it");
            return false;//log the error
        }

        log.debug("Scan for host (" + scanData.dbinfo + ") as user " + cred.getUsername());

        try {
            scanData.conn = conns.checkoutConnection(scanData.dbinfo, cred);
        } catch (Throwable iex) {

            if (Constants.CONN_MSG_NORETRY.equals(iex.getMessage()))
                return false;
            //log.warn("Failed to connect to "+scanData.dbinfo, iex);
            //reportOffline(dbinfo);
        }

        if (scanData.conn == null) {//try again
            try {
                scanData.conn = conns.checkoutConnection(scanData.dbinfo, cred);
            } catch (Throwable iex) {
                log.warn("Failed second attempt to connect to " + scanData.dbinfo, iex);
            }
        }

        return scanData.conn != null;

    }

    /**
     * 执行scan host操作。
     *
     * @param dbinfo dbinfo参数。
     * @return 是否满足条件。
     */
    private boolean scanHost(DBInstanceInfo dbinfo) {
        boolean status = false;
        ScanData scanData = new ScanData();
        scanData.dbinfo = dbinfo;

        try {
            List<AlertSubscribers.Subscription> subscripts = this.frameworkContext.getMetricsDef().getUdmManager()
                    .getAlertSubscriptions().getSubscriptions(dbinfo.getDbGroupName(), dbinfo.getHostName());

            if (subscripts == null || subscripts.size() == 0)
                return false; //nothing to scan

            if (!tryConnect(scanData))
                return false;

            for (AlertSubscribers.Subscription subscript : subscripts)
                scanAlert(scanData, subscript);

        } catch (Exception ex) {
            log.warn("exception: " + dbinfo, ex);
        } finally {

            if (scanData.conn != null) {
                conns.checkinConnectionAndClose(scanData.conn);
                scanData.conn = null;
            }
        }

        log.debug("Done scan for host (" + dbinfo + ")");
        //TODO update with mysql status
        return status;
    }

    /**
     * 执行scan alert操作。
     *
     * @param scanData  scan data参数。
     * @param subscript subscript参数。
     */
    private void scanAlert(ScanData scanData, AlertSubscribers.Subscription subscript) {
        AlertDefinition def = this.frameworkContext.getMetricsDef().getUdmManager().getAlertByName(subscript.alertName);

        if (def == null) {
            log.warn("Cannot find alert definition for " + subscript.alertName);
            return;
        }

        String sqlText = def.getSqlText();
        Map<String, String> params = new HashMap<>();

        if (!AlertDefinition.SOURCE_SQL.equals(def.getSource()) || sqlText == null || sqlText.isEmpty())
            return;//either not our job, or bad definition

        if (def.getParams() != null && def.getParams().size() > 0)
            for (Map.Entry<String, String> param : def.getParams().entrySet()) {
                String key = param.getKey();
                String val = param.getValue();

                if (subscript.params != null && subscript.params.containsKey(key)) {
                    String val1 = subscript.params.get(key);

                    if (val1 != null && !val1.isEmpty())
                        val = val1;
                }

                params.put(key, val);
            }

        try {
            long startTime = System.currentTimeMillis();
            ResultList rList = this.frameworkContext.getQueryEngine().executeQuery(scanData.conn, sqlText, params, 0);
            boolean hasAlert = false;
            //now generate alert
            InstanceStates ist = this.frameworkContext.getInstanceStatesManager().getStates(scanData.dbinfo.getDbid());

            if (rList != null && rList.getRows().size() > 0) {

                if (ist.reportUserAlert(new Date(startTime), def.getName(), String.valueOf(rList.getRows().size()))) {
                    AlertEntry alertEntry = new AlertEntry(startTime, subscript.alertName, null, scanData.dbinfo.getDbGroupName(), scanData.dbinfo.getHostName());
                    this.frameworkContext.addAlert(alertEntry);
                    AlertReport ar = this.frameworkContext.createAlertReport(System.currentTimeMillis(), alertEntry);
                    ar.setUserDefinedAlertData(rList);
                    ar.saveAsText();
                    this.frameworkContext.emailAlert(alertEntry);
                    this.frameworkContext.getMetricDb().addNewAlert(Long.parseLong(sdf.format(startTime)), scanData.dbinfo.getDbid(), subscript.alertName, null);
                }

                hasAlert = true;
            } else if (ist != null && ist.getLastAlertType() != null && ist.getLastAlertType().equals(def.getName()))
                this.frameworkContext.getMetricDb().markAlertEnd(Long.parseLong(sdf.format(startTime)) - 100, scanData.dbinfo.getDbid(), System.currentTimeMillis(), subscript.alertName);


            log.info("AlertScan done on " + scanData.dbinfo + ", alert " + def.getName() + ", has alert: " + hasAlert);
        } catch (Throwable ex) {
            log.error("Failed to scan alert " + def.getName() + ", for DB " + scanData.dbinfo, ex);
        }
    }
}
