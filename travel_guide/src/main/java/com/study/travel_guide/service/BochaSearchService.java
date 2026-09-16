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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BochaSearchService {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    @Value("${bocha.api-key}")
    private String apiKey;

    @Value("${bocha.base-url}")
    private String baseUrl;

    public BochaSearchService(RestClient restClient, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
    }

    public List<SearchItem> search(String query, int count) {
        return search(query, count, true);
    }

    public List<SearchItem> search(String query, int count, boolean limitSites) {
        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("summary", true);
        body.put("count", count);
        if (limitSites) {
            body.put("include", "xiaohongshu.com|bilibili.com|douyin.com");
        }

        String json;
        try {
            json = restClient.post()
                    .uri(baseUrl + "/v1/web-search")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.error("bocha search failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BizException(502, "博查搜索调用失败(" + e.getStatusCode().value() + ")，请检查 bocha.api-key 与额度");
        }

        List<SearchItem> items = new ArrayList<>();
        try {
            JsonNode root = jsonMapper.readTree(json);
            JsonNode values = root.path("data").path("webPages").path("value");
            for (JsonNode v : values) {
                items.add(new SearchItem(
                        v.path("name").asText(),
                        v.path("url").asText(),
                        v.path("summary").asText(),
                        v.path("siteName").asText()));
            }
        } catch (Exception e) {
            log.warn("parse bocha response failed: {}", json, e);
        }
        return items;
    }
}
