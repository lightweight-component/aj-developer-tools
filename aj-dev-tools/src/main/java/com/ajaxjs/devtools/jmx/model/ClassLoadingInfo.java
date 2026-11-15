package com.ajaxjs.devtools.jmx.model;

import lombok.Data;

/**
 * 类加载信息
 */
@Data
public class ClassLoadingInfo {
    private Long totalLoadedClassCount;

    private Integer loadedClassCount;

    private Long unloadedClassCount;

}
