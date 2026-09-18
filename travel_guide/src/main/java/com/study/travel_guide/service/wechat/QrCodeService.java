package com.study.travel_guide.service.wechat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.study.travel_guide.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class QrCodeService {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final WechatAccessTokenService accessTokenService;

    @Value("${wechat.qrcode-page:pages/result/result}")
    private String qrcodePage;

    @Value("${wechat.qrcode-env-version:develop}")
    private String envVersion;

    public QrCodeService(RestClient restClient, JsonMapper jsonMapper, WechatAccessTokenService accessTokenService) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
        this.accessTokenService = accessTokenService;
    }

    public byte[] generateTripQrCode(String shareToken) {
        String token = accessTokenService.getAccessToken();
        String url = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token={token}";

        Map<String, Object> body = new HashMap<>();
        body.put("scene", "token=" + shareToken);
        body.put("page", qrcodePage);
        body.put("check_path", false);
        body.put("env_version", envVersion);

        byte[] image = restClient.post()
                .uri(url, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(byte[].class);

        if (image == null || image.length == 0) {
            throw new BizException(502, "小程序码生成返回空");
        }
        // 出错时微信返回 JSON（errcode），成功时返回图片二进制
        if (image[0] == '{') {
            try {
                JsonNode err = jsonMapper.readTree(new String(image, StandardCharsets.UTF_8));
                throw new BizException(502, "小程序码生成失败: " + err.path("errmsg").asText());
            } catch (BizException e) {
                throw e;
            } catch (Exception ignored) {
            }
        }
        return image;
    }
}
