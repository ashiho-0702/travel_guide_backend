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
            int status = root.path("status").asInt(-1);
            if (status != 0) {
                log.warn("[map] 地点搜索失败：keyword={}, status={}, message={}", keyword, status, root.path("message").asText());
                return null;
            }
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

    /**
     * 周边搜索，返回最近的 POI 名称（景点名），失败返回 null。
     */
    public String searchNearby(double lat, double lng) {
        String url = "https://apis.map.qq.com/ws/place/v1/search" +
                "?boundary=nearby({lat},{lng},3000)&filter=category={category}&key={key}";
        try {
            String json = restClient.get()
                    .uri(url, lat, lng, "旅游景点,教育机构,文化场馆", mapKey)
                    .retrieve()
                    .body(String.class);
            log.info("[map] 周边搜索响应（前 400 字符）：{}",
                    json.length() > 400 ? json.substring(0, 400) : json);
            JsonNode root = jsonMapper.readTree(json);
            int status = root.path("status").asInt(-1);
            if (status != 0) {
                log.warn("[map] 周边搜索失败，status={}, message={}", status, root.path("message").asText());
                return null;
            }
            JsonNode first = root.path("data").get(0);
            if (first == null || first.isMissingNode()) {
                log.warn("[map] 周边搜索无数据（该坐标 1 公里内无 POI）");
                return null;
            }
            String name = first.path("title").asText();
            return name == null || name.isBlank() ? null : name;
        } catch (Exception e) {
            log.warn("search nearby failed for {},{}: {}", lat, lng, e.getMessage());
            return null;
        }
    }
}
