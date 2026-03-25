package com.ajaxjs.devtools.hotcpu.model.vo;

import lombok.Data;

/**
 * 采样操作结果
 */
@Data
public class SamplingOperation {
    /**
     * 操作状态
     */
    private String status;

    /**
     * 操作消息
     */
    private String message;
}
