package com.yahoo.dba.perf.myperf.process.model;

import com.yahoo.dba.perf.myperf.common.DBInstanceInfo;
import com.yahoo.dba.perf.myperf.common.StateSnapshot;
import com.yahoo.dba.perf.myperf.db.DBConnectionWrapper;

/**
 * To hold intermiediate data
 */
public class ScanData {
    public DBInstanceInfo dbinfo = null;
    public DBConnectionWrapper conn = null;//find one connection is enough
    public StateSnapshot stateSnap = new StateSnapshot();
}