package com.study.travel_guide.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class TencentMapService {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${tencent.map-key}")
    private String mapKey;

    public TencentMapService(RestClient restClient, JsonMapper jsonMapper, StringRedisTemplate redisTemplate) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 地点搜索，返回 [lat, lng]，失败返回 null。结果按「城市+关键词」缓存 30 天。
     */
    public double[] searchLocation(String keyword, String city) {
        String cacheKey = "geo:" + city + ":" + keyword;
        String cached = null;
        try {
            cached = redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception ignored) {
        }
        if (cached != null) {
            try {
                String[] parts = cached.split(",");
                return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
            } catch (Exception ignored) {
            }
        }
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
            double[] result = new double[]{location.path("lat").asDouble(), location.path("lng").asDouble()};
            cache(cacheKey, result[0] + "," + result[1], Duration.ofDays(30));
            return result;
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
                "?boundary=nearby({lat},{lng},500)&filter=category={category}&key={key}";
        try {
            String json = restClient.get()
                    .uri(url, lat, lng, "旅游景点", mapKey)
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
                log.warn("[map] 周边搜索无数据（该坐标 500 米内无景点 POI）");
                return null;
            }
            String name = first.path("title").asText();
            return name == null || name.isBlank() ? null : name;
        } catch (Exception e) {
            log.warn("search nearby failed for {},{}: {}", lat, lng, e.getMessage());
            return null;
        }
    }

    /**
     * 步行路线信息（距离米 + 时长秒），失败返回 null。结果按坐标缓存 30 天。
     */
    public Map<String, Integer> routeInfo(double fromLat, double fromLng, double toLat, double toLng) {
        String cacheKey = "route:" + round(fromLat) + "," + round(fromLng) + ":" + round(toLat) + "," + round(toLng);
        String cached = null;
        try {
            cached = redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception ignored) {
        }
        if (cached != null) {
            try {
                String[] parts = cached.split(",");
                Map<String, Integer> info = new HashMap<>();
                info.put("distance", Integer.parseInt(parts[0]));
                info.put("duration", Integer.parseInt(parts[1]));
                return info;
            } catch (Exception ignored) {
            }
        }
        String url = "https://apis.map.qq.com/ws/direction/v1/walking/?from={from}&to={to}&key={key}";
        try {
            String json = restClient.get()
                    .uri(url, fromLat + "," + fromLng, toLat + "," + toLng, mapKey)
                    .retrieve()
                    .body(String.class);
            JsonNode root = jsonMapper.readTree(json);
            int status = root.path("status").asInt(-1);
            if (status != 0) {
                log.warn("[map] 路线规划失败，status={}, message={}", status, root.path("message").asText());
                return null;
            }
            JsonNode route = root.path("result").path("routes").get(0);
            Map<String, Integer> info = new HashMap<>();
            info.put("distance", route.path("distance").asInt());
            info.put("duration", route.path("duration").asInt());
            cache(cacheKey, info.get("distance") + "," + info.get("duration"), Duration.ofDays(30));
            return info;
        } catch (Exception e) {
            log.warn("[map] 路线规划失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 判断地点是否在中国境内。返回 null 表示无法判断（网络/服务异常，降级放行）。
     */
    public Boolean isInChina(String city) {
        if (city == null || city.isBlank()) {
            return false;
        }
        city = city.trim();
        String cacheKey = "geo:nation:" + city;
        String cached = null;
        try {
            cached = redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception ignored) {
        }
        if (cached != null) {
            return "1".equals(cached);
        }
        String url = "https://apis.map.qq.com/ws/geocoder/v1/?address={address}&key={key}";
        try {
            String json = restClient.get()
                    .uri(url, city, mapKey)
                    .retrieve()
                    .body(String.class);
            JsonNode root = jsonMapper.readTree(json);
            int status = root.path("status").asInt(-1);
            if (status != 0) {
                log.warn("[map] 地理编码无结果或异常（不缓存，下次重试）: city={}, status={}, message={}", city, status, root.path("message").asText());
                return false;
            }
            String nation = root.path("result").path("ad_info").path("nation").asText();
            boolean inChina = "中国".equals(nation) || "中华人民共和国".equals(nation);
            cache(cacheKey, inChina ? "1" : "0", Duration.ofDays(30));
            return inChina;
        } catch (Exception e) {
            log.warn("[map] 城市国别判断失败（降级放行）: city={}, {}", city, e.getMessage());
            return null;
        }
    }

    private String round(double v) {
        return String.format("%.4f", v);
    }

    private void cache(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("[cache] 缓存写入失败: {}", e.getMessage());
        }
    }
}
