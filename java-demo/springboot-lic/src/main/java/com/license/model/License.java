package com.license.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 许可证实体类
 */
@Data
@JsonPropertyOrder({"subject", "issuedTo", "hardwareId", "expireAt", "features"})
public class License {
    /**
     * 软件名称
     */
    private String subject;
    /**
     * 授权给谁
     */
    private String issuedTo;
    /**
     * 硬件指纹（主板序列号）
     */
    private String hardwareId;

    /**
     * 到期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expireAt;

    /**
     * 功能权限列表
     */
    private List<String> features;
    /**
     * 签名（JSON序列化时忽略）
     */
    private String signature;

    public License(String subject, String issuedTo, String hardwareId, LocalDate expireAt, List<String> features) {
        this.subject = subject;
        this.issuedTo = issuedTo;
        this.hardwareId = hardwareId;
        this.expireAt = expireAt;
        this.features = features;
    }

}