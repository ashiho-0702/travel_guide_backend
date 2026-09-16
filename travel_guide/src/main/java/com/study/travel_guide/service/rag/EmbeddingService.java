package com.study.travel_guide.service.rag;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.study.travel_guide.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmbeddingService {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    @Value("${embedding.base-url}")
    private String baseUrl;

    @Value("${embedding.api-key}")
    private String apiKey;

    @Value("${embedding.model}")
    private String model;

    public EmbeddingService(RestClient restClient, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
    }

    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    public List<float[]> embedBatch(List<String> texts) {
        Map<String, Object> body = Map.of("model", model, "input", texts);

        String json;
        try {
            json = restClient.post()
                    .uri(baseUrl + "/embeddings")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.error("embedding failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BizException(502, "Embedding 调用失败(" + e.getStatusCode().value() + ")，请检查 embedding.api-key");
        }

        try {
            JsonNode root = jsonMapper.readTree(json);
            JsonNode data = root.path("data");
            List<float[]> result = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode vec = item.path("embedding");
                float[] arr = new float[vec.size()];
                for (int i = 0; i < vec.size(); i++) {
                    arr[i] = (float) vec.get(i).asDouble();
                }
                result.add(arr);
            }
            return result;
        } catch (Exception e) {
            throw new BizException(500, "Embedding 返回解析失败: " + e.getMessage());
        }
    }
}
