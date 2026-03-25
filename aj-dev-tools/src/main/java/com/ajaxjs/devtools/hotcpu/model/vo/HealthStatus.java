package com.ajaxjs.devtools.hotcpu.model.vo;

import lombok.Data;

/**
 * 健康状态
 */
@Data
public class HealthStatus {
    /**
     * 服务状态
     */
    private String status;

    /**
     * 服务名称
     */
    private String service;

    /**
     * 采样状态
     */
    private String sampling;
}
