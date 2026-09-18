package com.study.travel_guide.service.wechat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.study.travel_guide.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Service
public class WechatAccessTokenService {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    @Value("${wechat.appid}")
    private String appid;

    @Value("${wechat.secret}")
    private String secret;

    // 单机内存缓存（多实例部署需改分布式缓存，否则各实例刷新会互相踢掉 token）
    private volatile String accessToken;
    private volatile long expireAt = 0;

    public WechatAccessTokenService(RestClient restClient, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
    }

    public String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < expireAt) {
            return accessToken;
        }
        synchronized (this) {
            if (accessToken != null && System.currentTimeMillis() < expireAt) {
                return accessToken;
            }
            String url = "https://api.weixin.qq.com/cgi-bin/token" +
                    "?grant_type=client_credential&appid={appid}&secret={secret}";
            String json;
            try {
                json = restClient.get()
                        .uri(url, appid, secret)
                        .retrieve()
                        .body(String.class);
            } catch (RestClientResponseException e) {
                log.error("wechat token failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new BizException(502, "微信 access_token 获取失败(" + e.getStatusCode().value() + ")");
            }
            try {
                JsonNode root = jsonMapper.readTree(json);
                accessToken = root.path("access_token").asText();
                if (accessToken == null || accessToken.isBlank()) {
                    throw new BizException(502, "微信 access_token 获取失败: " + root.path("errmsg").asText());
                }
                long expiresIn = root.path("expires_in").asLong(7200);
                expireAt = System.currentTimeMillis() + (expiresIn - 300) * 1000;
                return accessToken;
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                throw new BizException(500, "微信 access_token 解析失败: " + e.getMessage());
            }
        }
    }
}
