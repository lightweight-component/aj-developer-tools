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
import com.yahoo.dba.perf.myperf.db.DBConnectionWrapper;
import com.yahoo.dba.perf.myperf.db.UserDBConnections;

import java.io.File;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

@Slf4j
public class GlobalVariableChangeScanTask implements Runnable {

    private MyPerfContext context;
    private static final String STORAGE_DIR = "autoscan";
    @Setter
    @Getter
    private AppUser appUser;
    private UserDBConnections conns;
    java.text.SimpleDateFormat sdf;

    public GlobalVariableChangeScanTask(MyPerfContext context, AppUser user) {
        this.context = context;
        this.appUser = user;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("GlobalVariableChangeScanTask");
        conns = new UserDBConnections();
        conns.setAppUser(appUser.getName());
        conns.setFrameworkContext(context);

        File root = new File(new File(this.context.getFileReposirtoryPath()), STORAGE_DIR);

        //get all dbids
        List<DBInstanceInfo> dbs = new ArrayList<DBInstanceInfo>();
        for (Map.Entry<String, DBGroupInfo> e : context.getDbInfoManager().getClusters().entrySet()) {
            DBGroupInfo g = e.getValue();
            for (DBInstanceInfo i : g.getInstances()) {
                dbs.add(i);
            }
        }


        //now get current timestamp
        sdf = new java.text.SimpleDateFormat("yyyyMMddHHmmss");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (DBInstanceInfo db : dbs)//now we need scan for each db
        {
            ConfigBlock cb = scanHost(db);
            if (cb != null) {
                updateConfigBlock(db, cb);
            }
        }

    }

    private void updateConfigBlock(DBInstanceInfo db, ConfigBlock cb) {
        File root = new File(new File(this.context.getFileReposirtoryPath()), STORAGE_DIR);
        ConfigHistory ch = ConfigHistory.load(root, db);
        if (ch == null) ch = new ConfigHistory();//a brand new
        if (ch.updateLast(cb))//if any changes, update
            ch.store(root, db);

    }

    private ConfigBlock scanHost(DBInstanceInfo dbinfo) {
        DBCredential cred = DBUtils.findDBCredential(context, dbinfo.getDbGroupName(), appUser);
        if (cred == null) {
            log.info("No credential for cluster " + dbinfo.getDbGroupName() + ", skip it");
            return null;//log the error
        }
        log.info("Scan for host (" + dbinfo + ") as user " + cred.getUsername());
        DBConnectionWrapper conn = null;//find one connection is enough
        Statement stmt = null;
        ResultSet rs = null;
        ConfigBlock cb = null;
        try {
            conn = conns.checkoutConnection(dbinfo, cred);
            if (conn == null) {
                log.info("Failed to access " + dbinfo + ", skip it");
                return null;
            }
            String sqlText = "show global variables";
            stmt = conn.getConnection().createStatement();
            rs = stmt.executeQuery(sqlText);
            cb = new ConfigBlock();
            while (rs != null && rs.next()) {
                String key = rs.getString("VARIABLE_NAME").toUpperCase();
                if ("TIMESTAMP".equalsIgnoreCase(key)) continue;//exclude a timestamp variable
                cb.addVariable(key, rs.getString("VALUE"));
            }
            Calendar c = Calendar.getInstance();
            Date dt = c.getTime();
            cb.setTime(sdf.format(dt));
        } catch (Exception ex) {
            log.warn("exception", ex);
            //we should not used failed data
            return null;
        } finally {
            DBUtils.close(rs);
            DBUtils.close(stmt);
            if (conn != null) conns.checkinConnectionAndClose(conn);
        }
        log.info("Done configuration scan for host (" + dbinfo + ") as user " + cred.getUsername());
        return cb;
    }
}
