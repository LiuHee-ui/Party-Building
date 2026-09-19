package com.hongmai.boot.config;

import com.hongmai.boot.storage.LocalFileStorageService;
import com.hongmai.common.storage.FileStorageService;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文件存储装配。
 * 本期只有本地磁盘实现；接入对象存储时新增一个 provider=cos 的分支即可，
 * 业务代码只依赖 FileStorageService 接口，不需要改动。
 */
@Configuration
@EnableConfigurationProperties(ObjectStorageConfig.StorageProperties.class)
public class ObjectStorageConfig {

    @Data
    @ConfigurationProperties(prefix = "storage")
    public static class StorageProperties {
        /** local | cos —— 本期仅实现 local */
        private String provider = "local";
        private String localRoot = "./data/files";
        private String publicBaseUrl = "http://localhost:8080";
        /** 直传凭证有效期（秒） */
        private long uploadExpireSeconds = 300;
        /** 下载链接有效期（秒），台账导出用 */
        private long downloadExpireSeconds = 1800;
    }

    @Bean
    public FileStorageService fileStorageService(StorageProperties properties) {
        if (!"local".equalsIgnoreCase(properties.getProvider())) {
            throw new IllegalStateException(
                    "storage.provider=" + properties.getProvider() + " 尚未实现，本期仅支持 local");
        }
        return new LocalFileStorageService(properties.getLocalRoot(), properties.getPublicBaseUrl());
    }
}
