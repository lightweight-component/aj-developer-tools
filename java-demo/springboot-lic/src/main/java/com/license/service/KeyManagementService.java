package com.license.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

/**
 * 密钥管理服务
 */
@Service
@Slf4j
public class KeyManagementService {
    @Autowired
    private RSAUtil rsaUtil;

    /**
     * 内存中存储密钥（实际项目中应该从配置文件或密钥库加载）
     */
    @Getter
    private PrivateKey cachedPrivateKey;

    @Getter
    private PublicKey cachedPublicKey;

    /**
     * 生成新的密钥对
     */
    public Map<String, String> generateKeyPair() throws Exception {
        KeyPair keyPair = rsaUtil.generateKeyPair();

        String privateKeyPem = rsaUtil.privateKeyToPem(keyPair.getPrivate());
        String publicKeyPem = rsaUtil.publicKeyToPem(keyPair.getPublic());

        // 缓存密钥
        cachedPrivateKey = keyPair.getPrivate();
        cachedPublicKey = keyPair.getPublic();

        Map<String, String> result = new HashMap<>();
        result.put("privateKey", privateKeyPem);
        result.put("publicKey", publicKeyPem);

        log.info("新密钥对生成成功");
        return result;
    }

    /**
     * 加载私钥
     */
    public void loadPrivateKey(String privateKeyPem) throws Exception {
        cachedPrivateKey = rsaUtil.loadPrivateKeyFromPem(privateKeyPem);
        log.info("私钥加载成功");
    }

    /**
     * 加载公钥
     */
    public void loadPublicKey(String publicKeyPem) throws Exception {
        cachedPublicKey = rsaUtil.loadPublicKeyFromPem(publicKeyPem);
        log.info("公钥加载成功");
    }

    /**
     * 检查密钥是否已加载
     */
    public boolean isKeysLoaded() {
        return cachedPrivateKey != null && cachedPublicKey != null;
    }
}