package com.study.travel_guide.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 和风天气：城市搜索 + 实时 + 未来 3 天预报。任何失败都降级。
 * 注意：2026-06 起和风停用公共域名（geoapi/devapi.qweather.com），改用个人专属 API Host（控制台「设置」查），
 * 认证用 X-QW-Api-Key 请求头；响应为 gzip 压缩，需手动解压。
 */
@Slf4j
@Service
public class WeatherService {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    @Value("${weather.api-key:}")
    private String apiKey;

    @Value("${weather.api-host:}")
    private String apiHost;

    public WeatherService(RestClient restClient, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 返回城市未来 3 天预报：key 为日期(yyyy-MM-dd)，value 为「小雨 15~25°C」。失败返回 null。
     */
    public Map<String, String> forecast(String city) {
        String locationId = resolveLocationId(city);
        if (locationId == null) {
            return null;
        }
        JsonNode root = fetchJson("/v7/weather/3d?location=" + locationId);
        if (root == null) {
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
    }

    /**
     * 返回城市当前天气 + 未来 3 天预报（结构化），供前端展示。失败时返回部分字段（至少含 city）。
     */
    public Map<String, Object> weather(String city) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", city);
        String locationId = resolveLocationId(city);
        if (locationId == null) {
            return result;
        }
        JsonNode nowRoot = fetchJson("/v7/weather/now?location=" + locationId);
        if (nowRoot != null) {
            JsonNode n = nowRoot.path("now");
            String text = n.path("text").asText();
            if (text != null && !text.isBlank()) {
                Map<String, Object> now = new LinkedHashMap<>();
                now.put("text", text);
                now.put("temp", n.path("temp").asText());
                now.put("feelsLike", n.path("feelsLike").asText());
                now.put("humidity", n.path("humidity").asText());
                result.put("now", now);
            }
        }
        JsonNode forecastRoot = fetchJson("/v7/weather/3d?location=" + locationId);
        if (forecastRoot != null) {
            List<Map<String, Object>> forecast = new ArrayList<>();
            for (JsonNode d : forecastRoot.path("daily")) {
                Map<String, Object> day = new LinkedHashMap<>();
                day.put("date", d.path("fxDate").asText());
                day.put("text", d.path("textDay").asText());
                day.put("tempMax", d.path("tempMax").asText());
                day.put("tempMin", d.path("tempMin").asText());
                forecast.add(day);
            }
            if (!forecast.isEmpty()) {
                result.put("forecast", forecast);
            }
        }
        return result;
    }

    private JsonNode fetchJson(String apiPath) {
        try {
            byte[] bytes = restClient.get()
                    .uri(apiHost + apiPath)
                    .header("X-QW-Api-Key", apiKey)
                    .exchange((request, response) -> response.getBody().readAllBytes());
            String json = decompressIfGzip(bytes);
            JsonNode root = jsonMapper.readTree(json);
            if (!"200".equals(root.path("code").asText())) {
                log.warn("[weather] 请求失败 {}: code={}, body={}", apiPath, root.path("code").asText(), json);
                return null;
            }
            return root;
        } catch (Exception e) {
            log.warn("[weather] 请求异常 {}: {}", apiPath, e.getMessage());
            return null;
        }
    }

    private String resolveLocationId(String city) {
        try {
            byte[] bytes = restClient.get()
                    .uri(apiHost + "/geo/v2/city/lookup?location={city}", city)
                    .header("X-QW-Api-Key", apiKey)
                    .exchange((request, response) -> response.getBody().readAllBytes());
            String json = decompressIfGzip(bytes);
            JsonNode root = jsonMapper.readTree(json);
            if (!"200".equals(root.path("code").asText())) {
                log.warn("[weather] 城市定位失败: code={}, body={}", root.path("code").asText(), json);
                return null;
            }
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

    private String decompressIfGzip(byte[] bytes) throws IOException {
        if (bytes != null && bytes.length >= 2 && (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B) {
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(bytes));
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                gis.transferTo(bos);
                return bos.toString(StandardCharsets.UTF_8);
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
