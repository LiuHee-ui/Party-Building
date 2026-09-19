package com.hongmai.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 微信小程序配置。 */
@Data
@Configuration
@EnableConfigurationProperties(WechatProperties.Miniapp.class)
public class WechatProperties {

    @Data
    @ConfigurationProperties(prefix = "wechat.miniapp")
    public static class Miniapp {
        private String appId;
        private String appSecret;
        private String code2SessionUrl = "https://api.weixin.qq.com/sns/jscode2session";
        /** 调用微信接口的超时（毫秒）。微信偶发抖动时不能拖垮登录链路。 */
        private int timeoutMillis = 3000;
    }
}
