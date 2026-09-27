package com.study.travel_guide.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 高德地图 POI 搜索：按城市 + 景点名搜照片，供热门景点动态配图。失败降级返回 null。
 */
@Slf4j
@Service
public class AmapService {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    @Value("${amap.key:}")
    private String apiKey;

    public AmapService(RestClient restClient, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
    }

    public String searchPhotoUrl(String city, String keyword) {
        try {
            String json = restClient.get()
                    .uri("https://restapi.amap.com/v3/place/text?keywords={kw}&city={city}&key={key}&extensions=all&offset=1",
                            keyword, city, apiKey)
                    .retrieve()
                    .body(String.class);
            JsonNode root = jsonMapper.readTree(json);
            if (!"1".equals(root.path("status").asText())) {
                log.warn("[amap] 搜索照片失败 {} {}: status={}, info={}", city, keyword,
                        root.path("status").asText(), root.path("info").asText());
                return null;
            }
            JsonNode photo = root.path("pois").get(0).path("photos").get(0);
            String url = photo.path("url").asText();
            return url == null || url.isBlank() ? null : url;
        } catch (Exception e) {
            log.warn("[amap] 搜索照片失败 {} {}: {}", city, keyword, e.getMessage());
            return null;
        }
    }
}
