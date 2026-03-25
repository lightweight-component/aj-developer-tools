package com.ajaxjs.devtools.hotcpu.model.vo;

import lombok.Data;

import java.util.Map;

/**
 * 调试信息
 */
@Data
public class DebugInfo {
    /**
     * 是否启用采样
     */
    private boolean enabled;

    /**
     * 栈计数大小
     */
    private int stackCountSize;

    /**
     * 采样数据（前10条）
     */
    private Map<String, Integer> sampleData;
}
