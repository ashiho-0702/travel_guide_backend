package com.study.travel_guide.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class TencentMapService {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    @Value("${tencent.map-key}")
    private String mapKey;

    public TencentMapService(RestClient restClient, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 地点搜索，返回 [lat, lng]，失败返回 null。
     */
    public double[] searchLocation(String keyword, String city) {
        String url = "https://apis.map.qq.com/ws/place/v1/search" +
                "?keyword={kw}&boundary=region({city},0)&key={key}";
        try {
            String json = restClient.get()
                    .uri(url, keyword, city, mapKey)
                    .retrieve()
                    .body(String.class);
            JsonNode root = jsonMapper.readTree(json);
            JsonNode first = root.path("data").get(0);
            if (first == null || first.isMissingNode()) {
                return null;
            }
            JsonNode location = first.path("location");
            return new double[]{location.path("lat").asDouble(), location.path("lng").asDouble()};
        } catch (Exception e) {
            log.warn("geocode failed for {}: {}", keyword, e.getMessage());
            return null;
        }
    }
}
