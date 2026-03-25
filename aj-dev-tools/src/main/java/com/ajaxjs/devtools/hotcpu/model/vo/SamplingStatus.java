package com.ajaxjs.devtools.hotcpu.model.vo;

import lombok.Data;

/**
 * 采样状态
 */
@Data
public class SamplingStatus {
    /**
     * 是否启用采样
     */
    private boolean enabled;

    /**
     * 栈计数大小
     */
    private int stackCountSize;
}
