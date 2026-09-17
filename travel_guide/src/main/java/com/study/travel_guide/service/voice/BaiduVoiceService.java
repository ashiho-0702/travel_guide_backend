package com.study.travel_guide.service.voice;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.study.travel_guide.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class BaiduVoiceService {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    @Value("${baidu.api-key:}")
    private String apiKey;

    @Value("${baidu.secret-key:}")
    private String secretKey;

    private volatile String accessToken;
    private volatile long tokenExpireAt = 0;

    public BaiduVoiceService(RestClient restClient, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
    }

    public String asr(String audioBase64, String format) {
        byte[] audioBytes = Base64.getDecoder().decode(audioBase64);
        String token = getAccessToken();

        Map<String, Object> body = new HashMap<>();
        body.put("format", format == null || format.isBlank() ? "wav" : format);
        body.put("rate", 16000);
        body.put("channel", 1);
        body.put("cuid", "travel_guide");
        body.put("token", token);
        body.put("dev_pid", 1537);
        body.put("speech", audioBase64);
        body.put("len", audioBytes.length);

        String json;
        try {
            json = restClient.post()
                    .uri("https://vop.baidu.com/server_api")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.error("baidu asr failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BizException(502, "百度语音识别调用失败(" + e.getStatusCode().value() + ")");
        }

        try {
            JsonNode root = jsonMapper.readTree(json);
            int errNo = root.path("err_no").asInt(-1);
            if (errNo != 0) {
                throw new BizException(502, "百度语音识别失败: " + root.path("err_msg").asText());
            }
            JsonNode result = root.path("result");
            if (result.isArray() && result.size() > 0) {
                return result.get(0).asText();
            }
            return "";
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "百度语音识别返回解析失败: " + e.getMessage());
        }
    }

    public String tts(String text) {
        if (text == null || text.isBlank()) {
            throw new BizException("TTS 文本不能为空");
        }
        String token = getAccessToken();

        String form = "tex=" + encode(text)
                + "&tok=" + encode(token)
                + "&cuid=travel_guide"
                + "&ctp=1"
                + "&lan=zh";
        log.info("TTS 合成文本：{}", text);

        byte[] audio;
        try {
            audio = restClient.post()
                    .uri("https://tsn.baidu.com/text2audio")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientResponseException e) {
            log.error("baidu tts failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BizException(502, "百度语音合成调用失败(" + e.getStatusCode().value() + ")");
        }

        if (audio == null || audio.length == 0) {
            throw new BizException(502, "百度语音合成返回空音频");
        }
        // 出错时百度返回 JSON（err_no），成功时返回音频二进制
        if (audio[0] == '{') {
            try {
                JsonNode err = jsonMapper.readTree(new String(audio, StandardCharsets.UTF_8));
                throw new BizException(502, "百度语音合成失败: " + err.path("err_msg").asText());
            } catch (BizException e) {
                throw e;
            } catch (Exception ignored) {
                // 不是 JSON 错误，继续
            }
        }
        log.info("TTS 合成完成，音频 {} 字节", audio.length);
        return Base64.getEncoder().encodeToString(audio);
    }

    private String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireAt) {
            return accessToken;
        }
        synchronized (this) {
            if (accessToken != null && System.currentTimeMillis() < tokenExpireAt) {
                return accessToken;
            }
            String form = "grant_type=client_credentials"
                    + "&client_id=" + encode(apiKey)
                    + "&client_secret=" + encode(secretKey);

            String json;
            try {
                json = restClient.post()
                        .uri("https://aip.baidubce.com/oauth/2.0/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(String.class);
            } catch (RestClientResponseException e) {
                log.error("baidu token failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new BizException(502, "百度 access_token 获取失败(" + e.getStatusCode().value() + ")，请检查 baidu.api-key/secret-key");
            }

            try {
                JsonNode root = jsonMapper.readTree(json);
                accessToken = root.path("access_token").asText();
                if (accessToken == null || accessToken.isBlank()) {
                    throw new BizException(502, "百度 access_token 获取失败: " + root.path("error_description").asText());
                }
                long expiresIn = root.path("expires_in").asLong(2592000);
                tokenExpireAt = System.currentTimeMillis() + (expiresIn - 600) * 1000;
                return accessToken;
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                throw new BizException(500, "百度 access_token 解析失败: " + e.getMessage());
            }
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
