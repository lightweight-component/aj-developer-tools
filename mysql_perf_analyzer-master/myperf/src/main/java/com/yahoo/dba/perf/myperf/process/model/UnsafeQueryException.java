package com.yahoo.dba.perf.myperf.process.model;

/**
 * 表示unsafe query exception相关的类型。
 */
public class UnsafeQueryException extends Exception {
    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 创建UnsafeQueryException对象。
     *
     * @param msg msg参数。
     */
    public UnsafeQueryException(String msg) {
        super(msg);
    }
}