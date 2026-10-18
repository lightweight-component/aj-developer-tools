/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import com.yahoo.dba.perf.myperf.common.UserDefinedMetrics.UserDefinedMetricsException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;


/**
 * Customized alert definitions, used by alert scanner.
 *
 * @author xrao
 */
@Slf4j
public class AlertDefinition implements Serializable {
    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 7023147712745750436L;

    /**
     * 定义source sql常量。
     */
    public static final String SOURCE_SQL = "SQL"; //need an sql statement or build in sql
    /**
     * 定义source global status常量。
     */
    public static final String SOURCE_GLOBAL_STATUS = "GLOBAL_STATUS";//from global_status
    /**
     * 定义source metrics常量。
     */
    public static final String SOURCE_METRICS = "METRICS";//from metrics def scanned
    /**
     * 定义source other常量。
     */
    public static final String SOURCE_OTHER = "OTHER";//not defined yet, or future use
    /**
     * 定义metrics comparision greater than常量。
     */
    public static final String METRICS_COMPARISION_GREATER_THAN = "GT"; //alert if exceed threshold
    /**
     * 定义metrics comparision less than常量。
     */
    public static final String METRICS_COMPARISION_LESS_THAN = "LT";//alert of below threshold
    /**
     * 定义metrics value type value常量。
     */
    public static final String METRICS_VALUE_TYPE_VALUE = "LAST_VALUE";//use value
    /**
     * 定义metrics value type diff avg常量。
     */
    public static final String METRICS_VALUE_TYPE_DIFF_AVG = "DIFF_AVG";//use per second change
    /**
     * 定义metrics value type diff常量。
     */
    public static final String METRICS_VALUE_TYPE_DIFF = "DIFF";//use changes, for example, just detect if something happened

    /**
     * 保存name信息。
     */
    @Getter
    private String name; //an all upper case name, refer to table ALERT, column ALERT_TYPE
    //for length, currently defined as VARCHAR(30), Alphanumerics only
    //Cannot be an existing name, including built in ones
    /**
     * 保存source信息。
     */
    @Getter
    private String source = SOURCE_SQL;
    /**
     * 保存sql id信息。
     */
    @Getter
    @Setter
    private String sqlId;//if source is SQL, we need either a sqlId for builtin SQLs in sql.xml
    //or sqlText
    /**
     * 保存sql text信息。
     */
    @Getter
    @Setter
    private String sqlText;

    /**
     * 保存metric name信息。
     */
    @Getter
    @Setter
    private String metricName; //if type is metrics, the full name of the metrics, group.subgroup.metricname
    /**
     * 保存default threshold信息。
     */
    @Getter
    @Setter
    private float defaultThreshold; //if type is metrics, the default threshold
    /**
     * 保存metric comparison信息。
     */
    @Getter
    @Setter
    private String metricComparison = METRICS_COMPARISION_GREATER_THAN;
    /**
     * 保存metric value type信息。
     */
    @Getter
    @Setter
    private String metricValueType = METRICS_VALUE_TYPE_VALUE;//use last value, or changes per second

    /**
     * 保存params集合。
     */
    @Getter
    private Map<String, String> params = new HashMap<>();

    /**
     * 创建AlertDefinition对象。
     *
     * @param name name参数。
     */
    public AlertDefinition(String name) {
        this.name = name != null ? name.trim() : null;
    }

    /**
     * 设置source。
     *
     * @param source source参数。
     */
    public void setSource(String source) {

        if (SOURCE_SQL.equalsIgnoreCase(source))
            this.source = SOURCE_SQL;
        else if (SOURCE_METRICS.equalsIgnoreCase(source))
            this.source = SOURCE_METRICS;
        else if (SOURCE_GLOBAL_STATUS.equalsIgnoreCase(source))
            this.source = SOURCE_GLOBAL_STATUS;
        else this.source = SOURCE_OTHER;
    }

    /**
     * 添加param。
     *
     * @param name         name参数。
     * @param defaultValue default value参数。
     */
    public void addParam(String name, String defaultValue) {
        this.params.put(name, defaultValue);
    }

    /**
     * 创建from json。
     *
     * @param in in参数。
     * @return 处理结果。
     */
    public static AlertDefinition createFromJson(InputStream in) {
        AlertDefinition alert = null;
        try (JsonReader jsonReader = Json.createReader(in)) {
            JsonObject jsonObject = jsonReader.readObject();
            alert = new AlertDefinition(jsonObject.getString("name"));
            alert.setSource(jsonObject.getString("source"));
            alert.setMetricName(jsonObject.getString("metricName", null));
            alert.setMetricValueType(jsonObject.getString("metricValueType", null));
            alert.setMetricComparison(jsonObject.getString("metricComparison", null));
            try {
                alert.setDefaultThreshold(Float.parseFloat(jsonObject.getString("defaultThreshold", null)));
            } catch (Exception ex) {
            }

            alert.setSqlId(jsonObject.getString("sqlId", null));
            alert.setSqlText(jsonObject.getString("sqlText", null));

            JsonArray params = jsonObject.getJsonArray("params");

            if (params != null) {
                int mlen = params.size();

                for (int i = 0; i < mlen; i++) {
                    JsonObject mobj = params.getJsonObject(i);
                    alert.addParam(mobj.getString("name"),
                            mobj.getString("defaultValue"));
                }
            }

            return alert;
        } catch (Exception ex) {
            log.warn("Error to parse UDM", ex);
        }
        return null;
    }

    /**
     * 转换为json。
     *
     * @param html html参数。
     * @return 处理结果。
     */
    public String toJSON(boolean html) {
        StringBuilder mtrStr = new StringBuilder();
        mtrStr.append("{\r\n");
        mtrStr.append("\"name\": \"").append(name).append("\",\r\n");
        mtrStr.append("\"source\": \"").append(source).append("\",\r\n");

        if (this.metricName != null && !this.metricName.isEmpty()) {
            mtrStr.append("\"metricName\": \"").append(metricName).append("\",\r\n");

            if (this.metricComparison != null && !this.metricComparison.isEmpty())
                mtrStr.append("\"metricComparison\": \"").append(metricComparison).append("\",\r\n");

            if (this.metricValueType != null && !this.metricValueType.isEmpty())
                mtrStr.append("\"metricValueType\": \"").append(metricValueType).append("\",\r\n");

            mtrStr.append("\"defaultThreshold\": \"").append(defaultThreshold).append("\",\r\n");
        }

        if (this.sqlId != null && !this.sqlId.isEmpty())
            mtrStr.append("\"sqlId\": \"").append(sqlId).append("\",\r\n");

        if (this.sqlText != null && !this.sqlText.isEmpty()) mtrStr.append("\"sqlText\": \"")
                .append(html ? CommonUtils.escapeJsonHTML(sqlText) : CommonUtils.escapeJson(sqlText)).append("\",\r\n");

        mtrStr.append("\"params\":[");
        int cnt = 0;

        for (Map.Entry<String, String> e : this.params.entrySet()) {

            if (cnt > 0)
                mtrStr.append(",\r\n");

            mtrStr.append("{\"name\": \"").append(e.getKey()).append("\",\"defaultValue\":\"")
                    .append(e.getValue())
                    .append("\"}");
        }

        mtrStr.append("]}");
        return mtrStr.toString();
    }


    /**
     * 执行validate操作。
     */
    public void validate() throws UserDefinedMetricsException {
        StringBuilder sb = new StringBuilder();
        boolean valid = true;

        if (!Metric.isAlphaNumericUnderscoreNotNull(name, 30)) {
            valid = false;
            sb.append("ALERT name " + name + " is not valid. Please use alphanumeric and underscore with max length of 30. ");
        }

        if ("SQL".equalsIgnoreCase(source))
            if ((sqlText == null || sqlText.isEmpty()) && (sqlId == null || sqlId.isEmpty())) {
                sb.append("Please provide SQL text for customized SQL. ");
                valid = false;
            } else {
                sqlText = sqlText.trim();

                if (!sqlText.toLowerCase().startsWith("select") || sqlText.indexOf(';') >= 0) {
                    sb.append("The SQL statement must start with select and no semi column is allowed. ");
                    valid = false;
                }
            }


        for (Map.Entry<String, String> e : this.params.entrySet())
            if (!Metric.isAlphaNumericUnderscoreNotNull(e.getKey(), 0)) {
                valid = false;
                sb.append("Alert param name ").append(e.getKey()).append(" is not valid. Please use alphanumeric and underscore.");
            }
        //TODO value


        if (!valid)
            throw new UserDefinedMetricsException(sb.toString());
    }

}
