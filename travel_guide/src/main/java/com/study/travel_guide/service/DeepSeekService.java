package com.study.travel_guide.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.study.travel_guide.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
public class DeepSeekService {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.model}")
    private String model;

    public DeepSeekService(RestClient restClient, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
    }

    public JsonNode generateJson(String systemPrompt, String userPrompt) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        JsonNode root = postChat(body);
        String content = root.path("choices").get(0).path("message").path("content").asText();
        try {
            return jsonMapper.readTree(content);
        } catch (Exception e) {
            throw new BizException(500, "AI 返回解析失败: " + e.getMessage());
        }
    }

    public JsonNode chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }
        return postChat(body);
    }

    public JsonNode streamGenerateJson(String systemPrompt, String userPrompt, Consumer<String> onToken) {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        );
        StringBuilder sb = new StringBuilder();
        streamContent(messages, null, token -> {
            sb.append(token);
            onToken.accept(token);
        });
        try {
            return jsonMapper.readTree(sb.toString());
        } catch (Exception e) {
            throw new BizException(500, "AI 返回解析失败: " + e.getMessage());
        }
    }

    private void streamContent(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
                               Consumer<String> onToken) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", true);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }
        try {
            restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, response) -> {
                        readSseStream(response.getBody(), onToken);
                        return null;
                    });
        } catch (RestClientResponseException e) {
            log.error("deepseek stream failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BizException(502, "DeepSeek 调用失败(" + e.getStatusCode().value() + ")，请检查 deepseek.api-key 与余额");
        }
    }

    private void readSseStream(InputStream in, Consumer<String> onToken) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) {
                    break;
                }
                try {
                    JsonNode node = jsonMapper.readTree(data);
                    String token = node.path("choices").get(0).path("delta").path("content").asText();
                    if (!token.isEmpty()) {
                        onToken.accept(token);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private JsonNode postChat(Map<String, Object> body) {
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
            log.error("deepseek call failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BizException(502, "DeepSeek 调用失败(" + e.getStatusCode().value() + ")，请检查 deepseek.api-key 与余额");
        }
        try {
            return jsonMapper.readTree(json);
        } catch (Exception e) {
            throw new BizException(500, "AI 返回解析失败: " + e.getMessage());
        }
    }
}
