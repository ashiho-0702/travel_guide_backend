package com.study.travel_guide.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 和风天气：城市搜索 + 未来 3 天预报，供攻略生成时按天气排行程。任何失败都降级返回 null。
 */
@Slf4j
@Service
public class WeatherService {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    @Value("${weather.api-key:}")
    private String apiKey;

    @Value("${weather.base-url:https://devapi.qweather.com}")
    private String baseUrl;

    @Value("${weather.geo-url:https://geoapi.qweather.com}")
    private String geoUrl;

    public WeatherService(RestClient restClient, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 返回城市未来 3 天预报：key 为日期(yyyy-MM-dd)，value 为「小雨 15~25°C」。失败返回 null。
     */
    public Map<String, String> forecast(String city) {
        try {
            String locationId = resolveLocationId(city);
            if (locationId == null) {
                return null;
            }
            String json = restClient.get()
                    .uri(baseUrl + "/v7/weather/3d?location={id}&key={key}", locationId, apiKey)
                    .retrieve()
                    .body(String.class);
            JsonNode root = jsonMapper.readTree(json);
            if (!"200".equals(root.path("code").asText())) {
                log.warn("[weather] 预报失败: code={}", root.path("code").asText());
                return null;
            }
            Map<String, String> result = new LinkedHashMap<>();
            for (JsonNode d : root.path("daily")) {
                String date = d.path("fxDate").asText();
                String text = d.path("textDay").asText();
                String max = d.path("tempMax").asText();
                String min = d.path("tempMin").asText();
                if (date.isBlank() || text.isBlank()) {
                    continue;
                }
                result.put(date, text + " " + min + "~" + max + "°C");
            }
            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            log.warn("[weather] 天气查询失败，降级跳过: {}", e.getMessage());
            return null;
        }
    }

    private String resolveLocationId(String city) {
        try {
            String json = restClient.get()
                    .uri(geoUrl + "/v2/city/lookup?location={city}&key={key}", city, apiKey)
                    .retrieve()
                    .body(String.class);
            JsonNode root = jsonMapper.readTree(json);
            JsonNode first = root.path("location").get(0);
            if (first == null || first.isMissingNode()) {
                return null;
            }
            return first.path("id").asText();
        } catch (Exception e) {
            log.warn("[weather] 城市定位失败: {}", e.getMessage());
            return null;
        }
    }
}
