/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 表示sql parameter相关的类型。
 */
public class SqlParameter implements Serializable {

    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 定义type numeric常量。
     */
    public final static String TYPE_NUMERIC = "numeric";
    /**
     * 定义type nonbreakable string常量。
     */
    public final static String TYPE_NONBREAKABLE_STRING = "non_breakable_string";//no space, quote, , ; \r, \n allowed
    /**
     * 定义type break string常量。
     */
    public final static String TYPE_BREAK_STRING = "breakable_string";
    /**
     * 定义type quoted string常量。
     */
    public final static String TYPE_QUOTED_STRING = "single_quoted_string";//so we only need escape single quote

    /**
     * 保存name信息。
     */
    @Getter
    @Setter
    private String name;//actual parameter meaningful name
    /**
     * 保存data type信息。
     */
    @Getter
    private String dataType = TYPE_NONBREAKABLE_STRING;//value type, one of above defined value

    /**
     * 创建SqlParameter对象。
     */
    public SqlParameter() {

    }

    /**
     * 创建SqlParameter对象。
     *
     * @param name     name参数。
     * @param dataType data type参数。
     */
    public SqlParameter(String name, String dataType) {
        this.name = name;

        if (dataType != null)
            this.dataType = dataType;
    }

    /**
     * 设置data type。
     *
     * @param dataType data type参数。
     */
    public void setDataType(String dataType) {

        if (dataType != null)
            this.dataType = dataType;
    }

    /**
     * 执行need escape操作。
     *
     * @return 是否满足条件。
     */
    public boolean needEscape() {
        return TYPE_BREAK_STRING.equalsIgnoreCase(this.dataType)
                || TYPE_QUOTED_STRING.equalsIgnoreCase(this.dataType);
    }

    /**
     * 判断valid value。
     *
     * @param input input参数。
     * @return 是否满足条件。
     */
    public boolean isValidValue(String input) {
        //check if numeric type
        if (TYPE_NUMERIC.equalsIgnoreCase(this.dataType))
            try {
                BigDecimal dec = new BigDecimal(input);
                return true;
            } catch (Exception ex) {
                return false;
            }
        else if (TYPE_NONBREAKABLE_STRING.equalsIgnoreCase(this.dataType)) {

            if (input == null)
                return true;
            else if (input.indexOf(' ') >= 0) return false;
            else if (input.indexOf('\t') >= 0) return false;
            else if (input.indexOf('\n') >= 0) return false;
            else if (input.indexOf('\r') >= 0) return false;
            else if (input.indexOf(';') >= 0) return false;
            else if (input.indexOf(',') >= 0) return false;

            return true;
        }

        return true;
    }

    /**
     * 执行escape single quote操作。
     *
     * @param input input参数。
     * @return 处理结果。
     */
    public static String escapeSingleQuote(String input) {

        if (input == null)
            return null;

        return input.replaceAll("'", "''");
    }
}
