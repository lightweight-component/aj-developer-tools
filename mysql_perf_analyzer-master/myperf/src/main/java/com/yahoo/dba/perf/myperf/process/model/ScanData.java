package com.yahoo.dba.perf.myperf.process.model;

import com.yahoo.dba.perf.myperf.common.DBInstanceInfo;
import com.yahoo.dba.perf.myperf.common.StateSnapshot;
import com.yahoo.dba.perf.myperf.db.DBConnectionWrapper;

/**
 * To hold intermiediate data
 */
public class ScanData {
    /**
     * 保存dbinfo信息。
     */
    public DBInstanceInfo dbinfo = null;
    /**
     * 保存conn信息。
     */
    public DBConnectionWrapper conn = null;//find one connection is enough
    /**
     * 保存state snap信息。
     */
    public StateSnapshot stateSnap = new StateSnapshot();
}