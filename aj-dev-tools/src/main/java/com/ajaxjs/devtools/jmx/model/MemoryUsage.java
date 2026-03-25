package com.ajaxjs.devtools.jmx.model;

import lombok.Data;

/**
 * Unit: Byte
 */
@Data
public class MemoryUsage {
    private long init;
    private long used;
    private long committed;
    private long max;
}
