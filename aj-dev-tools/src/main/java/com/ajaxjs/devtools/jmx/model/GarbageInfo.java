package com.ajaxjs.devtools.jmx.model;

import lombok.Data;

/**
 * 垃圾回收信息
 */
@Data
public class GarbageInfo {
    private String name;

    private Long collectionCount;

    private String[] memoryPoolNames;
}
