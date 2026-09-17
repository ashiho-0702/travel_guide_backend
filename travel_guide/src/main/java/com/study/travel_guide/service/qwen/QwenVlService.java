package com.study.travel_guide.service.qwen;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.study.travel_guide.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class QwenVlService {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    @Value("${qwen.api-key:}")
    private String apiKey;

    @Value("${qwen.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${qwen.model:qwen-vl-plus}")
    private String model;

    public QwenVlService(RestClient restClient, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 识别图片中的景点名。image 可以是公网 URL 或 data:image/...;base64,... 字符串。
     */
    public String identifyAttraction(String image) {
        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("type", "image_url");
        imagePart.put("image_url", Map.of("url", image));

        Map<String, Object> textPart = Map.of(
                "type", "text",
                "text", "识别图片中的景点或地标，只输出景点名称，不要输出其他内容");

        Map<String, Object> userMessage = Map.of(
                "role", "user",
                "content", List.of(imagePart, textPart));

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(userMessage));

        String json;
        try {
            json = restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.error("qwen vl failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BizException(502, "通义千问识图失败(" + e.getStatusCode().value() + ")，请检查 qwen.api-key");
        }

        try {
            JsonNode root = jsonMapper.readTree(json);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            return content == null ? null : content.trim();
        } catch (Exception e) {
            throw new BizException(500, "通义千问返回解析失败: " + e.getMessage());
        }
    }
}
