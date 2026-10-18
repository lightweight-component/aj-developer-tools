/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Represent a predefined sql statement.
 * Predefined sql has a handle name and position based place holder like &p_nn,
 * where nn starts from 1.
 *
 * @author xrao
 */
public class Sql implements Serializable {
    private static final long serialVersionUID = 1L;

    //keep those for Oracle expansion
    public final static String GV_PREFIX = "g_";
    public final static String GV_INST = "&inst_id";
    public final static String DBID = "&dbid";
    @Getter
    @Setter
    private boolean useDbid = false;//for AWR, if true, we need lookup dbid
    @Getter
    @Setter
    private boolean useSnapId;//for awr. When it is used, we have to have
    //begin_interval_time and end_interval_time in parameter list
    @Getter
    @Setter
    private boolean expandRow;//expand a single row to multiple columns
    @Getter
    @Setter
    private boolean errorInline = false;//if error and without result set, send response back with a column error

    @Getter
    @Setter
    private String handle;//name of the handle. Has to be unique
    @Getter
    @Setter
    private String sqlText;//sql text, for default
    @Getter
    @Setter
    private String queryClass;//query is generated from code. the java class name
    @Getter
    @Setter
    private String comments;//comments for the sql if any
    @Getter
    @Setter
    private boolean replace;//if true, use string replace rather than prepared statement.
    //Always for MySQL
    @Getter
    @Setter
    private int paramCount = 0;//total parameters, if any. Parameter will start from &p_1 to &p_{paramCount}
    //private java.util.List<String> paramNames = new java.util.ArrayList<String>();//comments or names for the parameters
    @Getter
    private List<SqlParameter> parameters = new ArrayList<>();//comments or names for the parameters
    @Getter
    @Setter
    private String queryProcessor;//customized executor

    //When requested metrics are contained in rows, flat function will transpose it to rows.
    @Getter
    private List<String> keyList = new ArrayList<>();//used for flat rows to columns
    @Getter
    private List<String> valueList = new ArrayList<>();//used for flat rows to columns
    @Getter
    @Setter
    private String flatKey;//allow to flat a single key
    @Getter
    private List<String> flatValueList = new ArrayList<>();//create new columns with those value
    @Getter
    @Setter
    private Map<String, String> flatValueAbbrMap = new LinkedHashMap<>();//create new columns with those value
    @Getter
    private LinkedHashMap<String, String> metrics = new LinkedHashMap<>();

    //SQL specific to a version
    private ArrayList<VSql> vsqls = new ArrayList<>();

    /**
     * SQL with restriction on version
     *
     * @author xrao
     */
    public static class VSql {
        @Getter
        @Setter
        private String text;
        @Getter
        @Setter
        private String maxVersion;
        @Getter
        @Setter
        private String minVersion;

        public VSql(String text, String minVersion, String maxVersion) {
            this.setText(text);
            this.setMaxVersion(maxVersion);
            this.setMinVersion(minVersion);
        }

    }

    public Sql() {
    }

    public String getSqlText(String version) {

        if (version == null)
            return this.getSqlText();

        for (VSql s : this.vsqls) {

            if (s.getMinVersion() != null && version.compareTo(s.getMinVersion()) < 0)
                continue;

            if (s.getMaxVersion() != null && version.compareTo(s.getMaxVersion()) > 0)
                continue;

            return s.getText();
        }

        return this.getSqlText();
    }

    public void addVSql(String text, String minVersion, String maxVersion) {
        this.vsqls.add(new VSql(text, minVersion, maxVersion));
    }

    /**
     * based on array index, not placeholder index
     *
     * @param i
     * @return
     */
    public SqlParameter getSqlParameter(int index) {

        if (this.parameters.size() > index)
            return this.parameters.get(index);

        return null;
    }

    /**
     * 1 based index
     *
     * @param name
     * @return
     */
    public int getParamIndex(String name) {

        for (int i = 0; i < this.parameters.size(); i++)
            if (name.equalsIgnoreCase(this.parameters.get(i).getName()))
                return i + 1;

        return -1;
    }

    public void addParameter(String name, String valueType) {
        this.parameters.add(new SqlParameter(name, valueType));
    }

}
