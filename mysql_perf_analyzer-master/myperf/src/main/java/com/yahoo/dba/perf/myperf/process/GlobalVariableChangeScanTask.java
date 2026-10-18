/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.process;

import com.yahoo.dba.perf.myperf.common.*;
import com.yahoo.dba.perf.myperf.db.DBConnectionWrapper;
import com.yahoo.dba.perf.myperf.db.UserDBConnections;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 表示global variable change scan task相关的类型。
 */
@Slf4j
public class GlobalVariableChangeScanTask implements Runnable {
    /**
     * 保存context信息。
     */
    private final MyPerfContext context;
    /**
     * 定义storage dir常量。
     */
    private static final String STORAGE_DIR = "autoscan";

    /**
     * 保存app user信息。
     */
    @Setter
    @Getter
    private AppUser appUser;
    /**
     * 保存conns信息。
     */
    private UserDBConnections conns;
    /**
     * 保存sdf信息。
     */
    SimpleDateFormat sdf;

    /**
     * 创建GlobalVariableChangeScanTask对象。
     *
     * @param context context参数。
     * @param user    user参数。
     */
    public GlobalVariableChangeScanTask(MyPerfContext context, AppUser user) {
        this.context = context;
        this.appUser = user;
    }

    /**
     * 执行run操作。
     */
    @Override
    public void run() {
        Thread.currentThread().setName("GlobalVariableChangeScanTask");
        conns = new UserDBConnections();
        conns.setAppUser(appUser.getName());
        conns.setFrameworkContext(context);
        File root = new File(new File(this.context.getFileReposirtoryPath()), STORAGE_DIR);

        //get all dbids
        List<DBInstanceInfo> dbs = new ArrayList<>();

        for (Map.Entry<String, DBGroupInfo> e : context.getDbInfoManager().getClusters().entrySet()) {
            DBGroupInfo g = e.getValue();
            dbs.addAll(g.getInstances());
        }

        //now get current timestamp
        sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        for (DBInstanceInfo db : dbs) {//now we need to scan for each db
            ConfigBlock cb = scanHost(db);

            if (cb != null)
                updateConfigBlock(db, cb);
        }
    }

    /**
     * 更新config block。
     *
     * @param db db参数。
     * @param cb cb参数。
     */
    private void updateConfigBlock(DBInstanceInfo db, ConfigBlock cb) {
        File root = new File(new File(this.context.getFileReposirtoryPath()), STORAGE_DIR);
        ConfigHistory ch = ConfigHistory.load(root, db);

        if (ch == null)
            ch = new ConfigHistory();//a brand new

        if (ch.updateLast(cb))//if any changes, update
            ch.store(root, db);
    }

    /**
     * 执行scan host操作。
     *
     * @param dbinfo dbinfo参数。
     * @return 处理结果。
     */
    private ConfigBlock scanHost(DBInstanceInfo dbinfo) {
        DBCredential cred = DBUtils.findDBCredential(context, dbinfo.getDbGroupName(), appUser);

        if (cred == null) {
            log.info("No credential for cluster " + dbinfo.getDbGroupName() + ", skip it");
            //log the error
            return null;
        }

        log.info("Scan for host (" + dbinfo + ") as user " + cred.getUsername());
        //find one connection is enough
        DBConnectionWrapper conn = null;
        ConfigBlock cb;

        try {
            conn = conns.checkoutConnection(dbinfo, cred);

            if (conn == null) {
                log.info("Failed to access " + dbinfo + ", skip it");
                return null;
            }

            String sqlText = "show global variables";
            try (Statement stmt = conn.getConnection().createStatement();
                 ResultSet rs = stmt.executeQuery(sqlText)) {
                cb = new ConfigBlock();

                while (rs != null && rs.next()) {
                    String key = rs.getString("VARIABLE_NAME").toUpperCase();

                    if ("TIMESTAMP".equalsIgnoreCase(key)) // exclude a timestamp variable
                        continue;

                    cb.addVariable(key, rs.getString("VALUE"));
                }

                Calendar c = Calendar.getInstance();
                Date dt = c.getTime();
                cb.setTime(sdf.format(dt));
            }
        } catch (Exception ex) {
            log.warn("exception", ex);

            return null; // we should be not used failed data
        } finally {

            if (conn != null)
                conns.checkinConnectionAndClose(conn);
        }

        log.info("Done configuration scan for host (" + dbinfo + ") as user " + cred.getUsername());
        return cb;
    }
}
