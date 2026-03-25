package com.ajaxjs.devtools.jmx.model;

import lombok.Data;

/**
 * 类加载信息
 */
@Data
public class ClassLoadingInfo {
    private long totalLoadedClassCount;

    private int loadedClassCount;

    private long unloadedClassCount;

}
