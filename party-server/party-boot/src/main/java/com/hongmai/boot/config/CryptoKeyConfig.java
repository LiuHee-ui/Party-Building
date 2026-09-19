package com.hongmai.boot.config;

import com.hongmai.common.crypto.CryptoUtil;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 敏感字段加密密钥装配（承接 spec N2）。
 * 启动即初始化，密钥缺失直接启动失败——宁可起不来，也不要用空密钥静默加密。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(CryptoKeyConfig.CryptoProperties.class)
public class CryptoKeyConfig {

    @Data
    @ConfigurationProperties(prefix = "crypto")
    public static class CryptoProperties {
        /** AES 密钥的 Base64，长度须为 16/24/32 字节。生产环境改由环境变量注入。 */
        private String key;
    }

    private final CryptoProperties properties;

    public CryptoKeyConfig(CryptoProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(properties.getKey())) {
            throw new IllegalStateException("crypto.key 未配置，敏感字段加密无法启用");
        }
        CryptoUtil.initKey(properties.getKey());
        log.info("敏感字段加密密钥已初始化");
    }
}
