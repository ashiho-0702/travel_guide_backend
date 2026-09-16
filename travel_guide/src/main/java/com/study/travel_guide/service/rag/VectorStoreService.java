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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Zilliz Cloud（托管 Milvus）REST API 封装。
 * Milvus REST v2 字段名以官方文档为准，若接入时报字段错误，对照
 * https://docs.zilliz.com/reference/restful 微调 create/insert/search 的 body。
 */
@Slf4j
@Service
public class VectorStoreService {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    @Value("${vectorstore.base-url}")
    private String baseUrl;

    @Value("${vectorstore.api-key}")
    private String apiKey;

    @Value("${vectorstore.collection}")
    private String collection;

    private final AtomicBoolean collectionEnsured = new AtomicBoolean(false);

    public VectorStoreService(RestClient restClient, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
    }

    public void ensureCollection(int dimension) {
        Map<String, Object> body = new HashMap<>();
        body.put("collectionName", collection);
        body.put("dimension", dimension);
        body.put("metricType", "COSINE");
        body.put("primaryFieldName", "id");
        body.put("vectorFieldName", "vector");
        body.put("autoID", true);
        post("/collections/create", body);
    }

    public void ensureCollectionIfNeeded(int dimension) {
        if (collectionEnsured.get()) {
            return;
        }
        synchronized (this) {
            if (collectionEnsured.get()) {
                return;
            }
            try {
                ensureCollection(dimension);
                collectionEnsured.set(true);
            } catch (BizException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                if (msg.contains("exist") || msg.contains("存在")) {
                    collectionEnsured.set(true);
                } else {
                    log.warn("create collection failed (will retry next call): {}", msg);
                }
            }
        }
    }

    public void upsert(List<float[]> vectors, List<String> texts, String city) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("vector", vectors.get(i));
            row.put("text", texts.get(i));
            row.put("city", city);
            rows.add(row);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("collectionName", collection);
        body.put("data", rows);
        post("/entities/insert", body);
    }

    public List<RetrievedDoc> search(float[] queryVector, int topK, String city) {
        Map<String, Object> body = new HashMap<>();
        body.put("collectionName", collection);
        body.put("data", List.of(queryVector));
        body.put("annsField", "vector");
        body.put("limit", topK);
        body.put("outputFields", List.of("text"));
        if (city != null && !city.isBlank()) {
            body.put("filter", "city == \"" + city + "\"");
        }

        JsonNode root = post("/entities/search", body);
        List<RetrievedDoc> docs = new ArrayList<>();
        for (JsonNode item : root.path("data")) {
            docs.add(new RetrievedDoc(
                    item.path("text").asText(),
                    item.path("distance").asDouble()));
        }
        return docs;
    }

    private JsonNode post(String path, Object body) {
        String json;
        try {
            json = restClient.post()
                    .uri(baseUrl + "/v2/vectordb" + path)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.error("vector store failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BizException(502, "向量库调用失败(" + e.getStatusCode().value() + ")，请检查 vectorstore 配置");
        }
        try {
            JsonNode root = jsonMapper.readTree(json);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                throw new BizException(502, "向量库返回错误 code=" + code + ": " + root.path("message").asText());
            }
            return root;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "向量库返回解析失败: " + e.getMessage());
        }
    }
}
