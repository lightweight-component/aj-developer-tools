/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.process;

import lombok.Getter;
import lombok.Setter;

import com.yahoo.dba.perf.myperf.common.*;
import com.yahoo.dba.perf.myperf.db.DBConnectionWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Collections;

@Slf4j
@RequiredArgsConstructor
public class AlertReportRunner implements Runnable {
    private final MyPerfContext context;
    private final DBInstanceInfo dbInfo;
    private final long timestamp;
    private final AppUser appUser;

    @Getter
    @Setter
    private DBConnectionWrapper connection;

    @Getter
    @Setter
    private String alertReason;

    @Getter
    @Setter
    private String alertValue;

    private boolean hasRowInfo(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int clSize = md.getColumnCount();

        for (int i = 1; i <= clSize; i++) {
            String name = md.getColumnName(i);

            if ("ROWS_READ".equalsIgnoreCase(name) || "ROWS_EXAMINED".equalsIgnoreCase(name) || "ROWS_SENT".equalsIgnoreCase(name))
                return true;
        }

        return false;
    }

    private boolean hasRowsRead(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int clSize = md.getColumnCount();

        for (int i = 1; i <= clSize; i++) {
            String name = md.getColumnName(i);

            if ("ROWS_READ".equalsIgnoreCase(name))
                return true;
        }

        return false;
    }

    @Override
    public void run() {
        AlertEntry alertEntry = new AlertEntry(timestamp, alertReason, alertValue, dbInfo.getDbGroupName(), dbInfo.getHostName());
        this.context.getAlerts().addAlert(alertEntry);
        long reportTimestamp = System.currentTimeMillis();
        List<ProcessListEntry> prList = new ArrayList<>();
        LinkedHashMap<String, String> repMap = new LinkedHashMap<>();
        String innodbStatus = null;
        ResultList rList = null;
        ResultList trxList = null;
        ResultList clientList = null;

        try (Statement stmt = connection.getConnection().createStatement()) {
            stmt.setFetchSize(5000);

            try (ResultSet rs = stmt.executeQuery("select * from information_schema.processlist")) {
                boolean hasRowInfo = rs != null && hasRowInfo(rs);
                boolean hasRowRead = rs != null && hasRowsRead(rs);

                while (rs != null && rs.next()) {
                    ProcessListEntry ps = new ProcessListEntry(rs, hasRowInfo, hasRowRead);
                    prList.add(ps);
                }
            }

            if ("REPLLAG".equalsIgnoreCase(this.alertReason) || "REPLDOWN".equalsIgnoreCase(this.alertReason))
                try (ResultSet rs = stmt.executeQuery("show slave status")) {
                    int i = 0;
                    int cnt = rs.getMetaData().getColumnCount();

                    while (rs.next()) {
                        String suffix = "";

                        if (i > 0)
                            suffix = "_" + i;

                        for (int k = 0; k < cnt; k++)
                            repMap.put(rs.getMetaData().getColumnName(k + 1) + suffix, rs.getString(k + 1));

                        i++;
                    }
                }

            //if ("CPU".equalsIgnoreCase(alertReason) ||"THREAD".equalsIgnoreCase(alertReason)||"REPLLAG".equalsIgnoreCase(this.alertReason))
            {
                try (ResultSet rs = stmt.executeQuery("show engine innodb status")) {

                    if (rs != null && rs.next())
                        innodbStatus = rs.getString("Status");
                }

                try (ResultSet rs = stmt.executeQuery("select * from information_schema.innodb_locks")) {

                    if (rs != null)
                        rList = ResultListUtil.fromSqlResultSet(rs, 0);
                }

                try (ResultSet rs = stmt.executeQuery("select * from information_schema.INNODB_TRX where to_seconds(now()) - to_seconds(trx_started) > 60")) {

                    if (rs != null)
                        trxList = ResultListUtil.fromSqlResultSet(rs, 0);
                }
            }
            // TODO we need two snapshot to get useful data
            if ("CONNECT_FAILURE".equalsIgnoreCase(alertReason)) {
                //dump summary of processlist if possible
                String sql = "select user, host, command, count(*) conns from information_schema.processlist group by user, host, command";

                try (ResultSet rs = stmt.executeQuery(sql)) {

                    if (rs != null)
                        clientList = ResultListUtil.fromSqlResultSet(rs, 0);
                }
            }

        } catch (Exception ex) {
            log.warn("Error when retrieve alert detail", ex);
        } finally {
            //release pending report count
            this.context.getInstanceStatesManager().getStates(this.dbInfo.getDbid()).reportDone();
        }

        if (prList.size() > 0 || repMap.size() > 0) {
            //sort by sql text
            Collections.sort(prList);
            ProcessListSummary prSum = new ProcessListSummary();
            prSum.setProcessList(prList);
            prSum.setInnodbStatus(innodbStatus);
            prSum.setLockList(rList);
            prSum.setTrxList(trxList);
            //prSum.setClientList(clientList);
            prSum.setReportTimestamp(reportTimestamp);
            prSum.summarize();
            AlertReport ar = this.context.createAlertReport(reportTimestamp, alertEntry);
            ar.setProcessListSummary(prSum);

            if (repMap.size() > 0)
                ar.setReplicationSummary(repMap);

            ar.saveAsText();
        }

        if (this.dbInfo.isAlertEnabled())
            this.context.emailAlert(alertEntry);
    }

}
