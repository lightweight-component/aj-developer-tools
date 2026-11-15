package com.ajaxjs.devtools.jmx.model;

import lombok.Data;

/**
 * Unit: Byte
 */
@Data
public class MemoryUsage {
    private Long init;
    private Long used;
    private Long committed;
    private Long max;
}
