package com.license.service;

import com.license.model.License;
import com.license.model.LicenseContext;
import com.license.model.LicenseVerifyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 许可证启动验证器
 * 在应用启动时验证许可证并保存到上下文
 */
//@Component
@Slf4j
public class LicenseStartupValidator implements ApplicationRunner {
    @Autowired
    private LicenseService licenseService;

    @Autowired
    private KeyManagementService keyManagementService;

    @Value("${license.file.path:license.json}")
    private String licenseFilePath;

    @Value("${license.public.key.path:public.pem}")
    private String publicKeyPath;

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始执行许可证启动验证...");

        Path licensePath = Paths.get(licenseFilePath); // 检查许可证文件是否存在
        if (!Files.exists(licensePath))
            throw new RuntimeException("许可证文件不存在: " + licenseFilePath);

        String licenseJson;  // 读取许可证文件内容

        try {
            licenseJson = Files.readString(licensePath, StandardCharsets.UTF_8);
            log.debug("许可证文件读取成功，文件大小: {} 字节", licenseJson.length());

            // 加载公钥
            Path publicKeyFilePath = Paths.get(publicKeyPath);
            if (Files.exists(publicKeyFilePath)) {
                String publicKeyContent = Files.readString(publicKeyFilePath, StandardCharsets.UTF_8);
                keyManagementService.loadPublicKey(publicKeyContent);
                log.info("公钥文件加载成功");
            } else
                throw new RuntimeException("公钥文件不存在: " + publicKeyPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 验证许可证
        LicenseVerifyResult result = licenseService.verifyLicense(licenseJson, keyManagementService.getCachedPublicKey());

        if (!result.isValid()) {
            log.error("许可证验证失败: {}", result.getMessage());

            throw new RuntimeException("许可证验证失败，应用启动终止: " + result.getMessage());
        }

        License license = result.getLicense();  // 验证通过，记录许可证信息
        log.info("====== 许可证验证通过 ======");
        log.info("软件产品: {}", license.getSubject());
        log.info("授权对象: {}", license.getIssuedTo());
        log.info("到期时间: {}", license.getExpireAt());
        log.info("授权功能: {}", String.join(", ", license.getFeatures()));
        log.info("绑定硬件: {}", license.getHardwareId());
        log.info("============================");

        LicenseContext.setCurrentLicense(license); // 将许可证信息保存到应用上下文，供其他组件使用
    }
}