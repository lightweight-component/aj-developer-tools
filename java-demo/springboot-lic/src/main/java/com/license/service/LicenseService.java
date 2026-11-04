package com.license.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.license.model.License;
import com.license.model.LicenseVerifyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDate;

/**
 * 许可证服务类
 */
@Service
@Slf4j
public class LicenseService {
    @Autowired
    private RSAUtil rsaUtil;

    @Autowired
    private HardwareUtil hardwareUtil;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 生成许可证
     */
    public String generateLicense(License license, PrivateKey privateKey) throws Exception {
        if (license.getHardwareId() == null || license.getHardwareId().isEmpty())// 自动填充硬件指纹
            license.setHardwareId(hardwareUtil.getMotherboardSerial());

        String licenseData = createStandardizedLicenseJson(license); // 创建标准化的JSON数据（按固定顺序）
        log.debug("用于签名的许可证数据: {}", licenseData);

        String signature = rsaUtil.sign(licenseData, privateKey); // 使用私钥签名

        JsonNode jsonNode = objectMapper.readTree(licenseData); // 创建包含签名的完整许可证
        ((com.fasterxml.jackson.databind.node.ObjectNode) jsonNode).put("signature", signature);

        String result = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        log.info("许可证生成成功，授权给: {}", license.getIssuedTo());

        return result;
    }

    /**
     * 验证许可证
     */
    public LicenseVerifyResult verifyLicense(String licenseJson, PublicKey publicKey) {
        try {
            JsonNode jsonNode = objectMapper.readTree(licenseJson);

            // 提取签名
            if (!jsonNode.has("signature"))
                return new LicenseVerifyResult(false, "许可证缺少签名信息");

            String signature = jsonNode.get("signature").asText();

            // 移除签名字段，获取原始数据
            ((com.fasterxml.jackson.databind.node.ObjectNode) jsonNode).remove("signature");

            // 解析许可证对象
            License license = objectMapper.readValue(jsonNode.toString(), License.class);

            // 重新生成标准化的JSON数据用于验证
            String licenseData = createStandardizedLicenseJson(license);
            log.debug("用于验证的许可证数据: {}", licenseData);

            // 验证签名
            boolean signatureValid = rsaUtil.verify(licenseData, signature, publicKey);

            if (!signatureValid) {
                log.warn("签名验证失败 - 原始数据: {}", licenseData);
                return new LicenseVerifyResult(false, "许可证签名验证失败");
            }

            // 验证硬件指纹
            String currentHardwareId = hardwareUtil.getMotherboardSerial();

            if (!currentHardwareId.equals(license.getHardwareId()))
                return new LicenseVerifyResult(false, String.format("硬件指纹不匹配。期望: %s, 实际: %s", license.getHardwareId(), currentHardwareId));

            // 验证有效期
            if (license.getExpireAt().isBefore(LocalDate.now()))
                return new LicenseVerifyResult(false,
                        String.format("许可证已过期。到期时间: %s", license.getExpireAt()));

            log.info("许可证验证成功: {}", license.getIssuedTo());

            return new LicenseVerifyResult(true, "许可证验证成功", license);
        } catch (Exception e) {
            log.error("许可证验证失败", e);
            return new LicenseVerifyResult(false, "许可证格式错误: " + e.getMessage());
        }
    }

    /**
     * 创建标准化的许可证JSON数据
     * 确保字段顺序一致，用于签名和验证
     */
    private String createStandardizedLicenseJson(License license) {
        // 手动构建JSON以确保字段顺序一致
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"subject\":\"").append(escapeJson(license.getSubject())).append("\",");
        json.append("\"issuedTo\":\"").append(escapeJson(license.getIssuedTo())).append("\",");
        json.append("\"hardwareId\":\"").append(escapeJson(license.getHardwareId())).append("\",");
        json.append("\"expireAt\":\"").append(license.getExpireAt().toString()).append("\",");
        json.append("\"features\":[");

        if (license.getFeatures() != null && !license.getFeatures().isEmpty()) {
            for (int i = 0; i < license.getFeatures().size(); i++) {

                if (i > 0)
                    json.append(",");

                json.append("\"").append(escapeJson(license.getFeatures().get(i))).append("\"");
            }
        }

        json.append("]}");

        return json.toString();
    }

    /**
     * 转义JSON字符串中的特殊字符
     */
    private String escapeJson(String str) {
        if (str == null)
            return "";

        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}