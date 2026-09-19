package com.hongmai.auth.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongmai.auth.config.WechatProperties;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 微信小程序接口客户端。
 *
 * 只用 JDK 自带的 HttpClient，不额外引 SDK —— 只调一个接口，引 SDK 不划算。
 * 关键点：微信的接口「HTTP 200 也可能返回错误」，必须解析 body 里的 errcode，
 * 只看 HTTP 状态码会把「code 无效」当成登录成功。
 */
@Slf4j
@Component
public class WechatMiniappClient {

    private final WechatProperties.Miniapp properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WechatMiniappClient(WechatProperties.Miniapp properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getTimeoutMillis()))
                .build();
    }

    /** 用 wx.login 的 code 换取 openid。失败统一抛 2002。 */
    public String code2Session(String jsCode) {
        String url = properties.getCode2SessionUrl()
                + "?appid=" + encode(properties.getAppId())
                + "&secret=" + encode(properties.getAppSecret())
                + "&js_code=" + encode(jsCode)
                + "&grant_type=authorization_code";

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(properties.getTimeoutMillis()))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            JsonNode body = objectMapper.readTree(response.body());
            int errcode = body.path("errcode").asInt(0);
            if (errcode != 0) {
                // 40029 code 无效、45011 频率限制等，都归为「凭证无效」，不把微信原始报错透给客户端
                log.warn("code2session 失败 errcode={} errmsg={}", errcode, body.path("errmsg").asText());
                throw new BizException(ErrorCode.WECHAT_CODE_INVALID);
            }

            String openId = body.path("openid").asText(null);
            if (!StringUtils.hasText(openId)) {
                throw new BizException(ErrorCode.WECHAT_CODE_INVALID);
            }
            return openId;
        } catch (BizException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.WECHAT_API_ERROR);
        } catch (Exception e) {
            log.error("调用微信 code2session 异常", e);
            throw new BizException(ErrorCode.WECHAT_API_ERROR);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
