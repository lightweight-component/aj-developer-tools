/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represent a predefined sql statement.
 * Predefined sql has a handle name and position based place holder like &p_nn,
 * where nn starts from 1.
 *
 * @author xrao
 */
public class Sql implements Serializable {
    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 1L;

    //keep those for Oracle expansion
    /**
     * 定义gv prefix常量。
     */
    public final static String GV_PREFIX = "g_";
    /**
     * 定义gv inst常量。
     */
    public final static String GV_INST = "&inst_id";
    /**
     * 定义dbid常量。
     */
    public final static String DBID = "&dbid";
    /**
     * 标记use dbid状态。
     */
    @Getter
    @Setter
    private boolean useDbid = false;//for AWR, if true, we need lookup dbid
    /**
     * 标记use snap id状态。
     */
    @Getter
    @Setter
    private boolean useSnapId;//for awr. When it is used, we have to have
    //begin_interval_time and end_interval_time in parameter list
    /**
     * 标记expand row状态。
     */
    @Getter
    @Setter
    private boolean expandRow;//expand a single row to multiple columns
    /**
     * 标记error inline状态。
     */
    @Getter
    @Setter
    private boolean errorInline = false;//if error and without result set, send response back with a column error

    /**
     * 保存handle信息。
     */
    @Getter
    @Setter
    private String handle;//name of the handle. Has to be unique
    /**
     * 保存sql text信息。
     */
    @Getter
    @Setter
    private String sqlText;//sql text, for default
    /**
     * 保存query class信息。
     */
    @Getter
    @Setter
    private String queryClass;//query is generated from code. the java class name
    /**
     * 保存comments信息。
     */
    @Getter
    @Setter
    private String comments;//comments for the sql if any
    /**
     * 标记replace状态。
     */
    @Getter
    @Setter
    private boolean replace;//if true, use string replace rather than prepared statement.
    //Always for MySQL
    /**
     * 保存param count信息。
     */
    @Getter
    @Setter
    private int paramCount = 0;//total parameters, if any. Parameter will start from &p_1 to &p_{paramCount}
    //private java.util.List<String> paramNames = new java.util.ArrayList<String>();//comments or names for the parameters
    /**
     * 保存parameters集合。
     */
    @Getter
    private List<SqlParameter> parameters = new ArrayList<>();//comments or names for the parameters
    /**
     * 保存query processor信息。
     */
    @Getter
    @Setter
    private String queryProcessor;//customized executor

    //When requested metrics are contained in rows, flat function will transpose it to rows.
    /**
     * 保存key list集合。
     */
    @Getter
    private List<String> keyList = new ArrayList<>();//used for flat rows to columns
    /**
     * 保存value list集合。
     */
    @Getter
    private List<String> valueList = new ArrayList<>();//used for flat rows to columns
    /**
     * 保存flat key信息。
     */
    @Getter
    @Setter
    private String flatKey;//allow to flat a single key
    /**
     * 保存flat value list集合。
     */
    @Getter
    private List<String> flatValueList = new ArrayList<>();//create new columns with those value
    /**
     * 保存flat value abbr map集合。
     */
    @Getter
    @Setter
    private Map<String, String> flatValueAbbrMap = new LinkedHashMap<>();//create new columns with those value
    /**
     * 保存metrics信息。
     */
    @Getter
    private LinkedHashMap<String, String> metrics = new LinkedHashMap<>();

    //SQL specific to a version
    /**
     * 保存vsqls信息。
     */
    private ArrayList<VSql> vsqls = new ArrayList<>();

    /**
     * SQL with restriction on version
     *
     * @author xrao
     */
    public static class VSql {
        /**
         * 保存text信息。
         */
        @Getter
        @Setter
        private String text;
        /**
         * 保存max version信息。
         */
        @Getter
        @Setter
        private String maxVersion;
        /**
         * 保存min version信息。
         */
        @Getter
        @Setter
        private String minVersion;

        /**
         * 创建VSql对象。
         *
         * @param text       text参数。
         * @param minVersion min version参数。
         * @param maxVersion max version参数。
         */
        public VSql(String text, String minVersion, String maxVersion) {
            this.setText(text);
            this.setMaxVersion(maxVersion);
            this.setMinVersion(minVersion);
        }

    }

    /**
     * 创建Sql对象。
     */
    public Sql() {
    }

    /**
     * 获取sql text。
     *
     * @param version version参数。
     * @return 处理结果。
     */
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

    /**
     * 添加vsql。
     *
     * @param text       text参数。
     * @param minVersion min version参数。
     * @param maxVersion max version参数。
     */
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

    /**
     * 添加parameter。
     *
     * @param name      name参数。
     * @param valueType value type参数。
     */
    public void addParameter(String name, String valueType) {
        this.parameters.add(new SqlParameter(name, valueType));
    }

}
