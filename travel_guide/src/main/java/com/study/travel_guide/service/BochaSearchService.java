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

    private static final String SOCIAL_DOMAINS = "xiaohongshu.com|bilibili.com|douyin.com";
    private static final String ATTRACTION_DOMAINS = "ctrip.com|fliggy.com|qunar.com";
    private static final String FOOD_DOMAINS = "dianping.com";

    private static final String[] FOOD_KEYWORDS = {
            "美食", "好吃", "餐厅", "小吃", "人均", "营业时间", "商圈", "吃什么", "必吃", "夜宵", "火锅", "面馆", "奶茶", "甜品", "烧烤", "饭店"};
    private static final String[] ATTRACTION_KEYWORDS = {
            "门票", "票价", "开放时间", "预约", "几点", "景区", "景点", "入园", "游玩"};

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
            body.put("include", selectDomains(query));
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

    private String selectDomains(String query) {
        List<String> domains = new ArrayList<>();
        domains.add(SOCIAL_DOMAINS);

        boolean food = containsAny(query, FOOD_KEYWORDS);
        boolean attraction = containsAny(query, ATTRACTION_KEYWORDS);

        if (food && !attraction) {
            domains.add(FOOD_DOMAINS);
        } else if (attraction && !food) {
            domains.add(ATTRACTION_DOMAINS);
        } else {
            domains.add(ATTRACTION_DOMAINS);
            domains.add(FOOD_DOMAINS);
        }
        return String.join("|", domains);
    }

    private boolean containsAny(String text, String[] keywords) {
        if (text == null) {
            return false;
        }
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
