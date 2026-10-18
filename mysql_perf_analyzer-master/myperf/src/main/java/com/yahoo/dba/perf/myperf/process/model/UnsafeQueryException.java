package com.yahoo.dba.perf.myperf.process.model;

public class UnsafeQueryException extends Exception {
    private static final long serialVersionUID = 1L;

    public UnsafeQueryException(String msg) {
        super(msg);
    }
}