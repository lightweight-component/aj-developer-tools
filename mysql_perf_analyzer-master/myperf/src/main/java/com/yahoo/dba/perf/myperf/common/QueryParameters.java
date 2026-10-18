/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

/**
 * Wrap query parameters
 */
public class QueryParameters implements Serializable {

    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 6906313101027653783L;
    /**
     * 保存group信息。
     */
    @Getter
    private String group;//an identifier for the target database
    /**
     * 保存host信息。
     */
    @Getter
    private String host;//DB hostname
    /**
     * 保存port信息。
     */
    @Getter
    private String port;//DB port
    /**
     * 保存svc信息。
     */
    @Getter
    private String svc;//servicename
    /**
     * 保存sid信息。
     */
    @Getter
    private String sid;//sid
    /**
     * 保存sql信息。
     */
    @Getter
    private String sql;//an internal sql id
    /**
     * 保存target instance信息。
     */
    @Getter
    @Setter
    private int targetInstance = 0;//0 is invalid number, meaning no target instance is passed. If it is passed, it will be in higher priority than the one derived from host

    /**
     * 保存sql params集合。
     */
    @Getter
    private Map<String, String> sqlParams = new TreeMap<>();
    //parameter in p_1, p_2,..., name convention

    /**
     * 保存max rows信息。
     */
    @Getter
    @Setter
    private int maxRows = 5000;//maximum rows to return

    /**
     * 保存sql text信息。
     */
    @Getter
    @Setter
    private String sqlText;//So we can use it for non predefined queries.

    /**
     * 创建QueryParameters对象。
     */
    public QueryParameters() {

    }

    /**
     * 设置group。
     *
     * @param group group参数。
     */
    public void setGroup(String group) {

        if (group != null && group.trim().length() > 0)
            this.group = group.trim();
        else
            this.group = null;
    }

    /**
     * 设置host。
     *
     * @param host host参数。
     */
    public void setHost(String host) {

        if (host != null && host.trim().length() > 0)
            this.host = host.trim();
        else this.host = null;
    }

    /**
     * 设置port。
     *
     * @param port port参数。
     */
    public void setPort(String port) {

        if (port != null && port.trim().length() > 0)
            this.port = port.trim();
        else this.port = "1521";
    }

    /**
     * 设置svc。
     *
     * @param svc svc参数。
     */
    public void setSvc(String svc) {

        if (svc != null && svc.trim().length() > 0)
            this.svc = svc.trim();
        else this.svc = null;
    }

    /**
     * 设置sid。
     *
     * @param sid sid参数。
     */
    public void setSid(String sid) {

        if (sid != null && sid.trim().length() > 0)
            this.sid = sid.trim();
        else this.sid = null;
    }

    /**
     * 设置sql。
     *
     * @param sql sql参数。
     */
    public void setSql(String sql) {

        if (sql != null && sql.trim().length() > 0)
            this.sql = sql.trim();
        else this.sql = null;
    }

    /**
     * 转换为string。
     *
     * @return 处理结果。
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();

        sb.append("[id=").append(group).append("]");
        sb.append(",[host=").append(host).append("]");
        sb.append(",[port=").append(port).append("]");
        sb.append(",[svc=").append(svc).append("]");
        sb.append(",[sid=").append(sid).append("]");
        sb.append(",[sql=").append(sql).append("]");

        if (this.sqlParams.size() > 0)
            for (String key : this.sqlParams.keySet())
                sb.append(",[").append(key).append("=").append(this.sqlParams.get(key)).append("]");


        return sb.toString();
    }

    /**
     * Validate the parameters. Return null or empty if validated. Otherwise, error messages
     * TODO
     *
     * @return
     */
    public String validate() {
        StringBuilder sb = new StringBuilder();

        if (group == null)
            sb.append("group is required. ");

        if (host == null)
            sb.append("host is required. ");
        //if(svc==null&&sid==null)sb.append("one of svc and sid is required. ");
        if (sql == null)
            sb.append("sql is required. ");

        return sb.toString();
    }


}
