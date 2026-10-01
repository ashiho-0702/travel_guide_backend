package com.study.travel_guide.service.guide;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.study.travel_guide.entity.Trip;
import com.study.travel_guide.service.DeepSeekService;
import com.study.travel_guide.service.TripService;
import com.study.travel_guide.service.WeatherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 搭子主动提醒：预约提醒（进通话检查一次）+ 天气提醒（定时检查）。DeepSeek 生成提醒文本，Redis 去重。
 */
@Slf4j
@Service
public class BuddyReminderService {

    private static final String REMINDER_SYSTEM_PROMPT = """
            你是旅行搭子，用口语化、亲切的语气提醒用户，100 字内，适合语音播报，不要有任何 markdown。
            只输出 JSON：{"reminder": "提醒内容"}
            """;

    private final TripService tripService;
    private final WeatherService weatherService;
    private final DeepSeekService deepSeekService;
    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public BuddyReminderService(TripService tripService, WeatherService weatherService,
                                DeepSeekService deepSeekService, StringRedisTemplate redisTemplate,
                                JsonMapper jsonMapper) {
        this.tripService = tripService;
        this.weatherService = weatherService;
        this.deepSeekService = deepSeekService;
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
    }

    public String checkBookingReminder(Long userId, Long tripId) {
        String key = "remind:booking:" + userId + ":" + tripId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return null;
        }
        Trip trip = tripService.detailAsMember(userId, tripId);
        List<String> bookingSpots = collectBookingSpots(trip.getResult());
        if (bookingSpots.isEmpty()) {
            return null;
        }
        String userPrompt = "行程里需要预约的景点：" + String.join("、", bookingSpots)
                + "\n请提醒用户记得提前预约。";
        try {
            JsonNode result = deepSeekService.generateJson(REMINDER_SYSTEM_PROMPT, userPrompt);
            String reminder = result.path("reminder").asText();
            if (reminder == null || reminder.isBlank()) {
                return null;
            }
            redisTemplate.opsForValue().set(key, "1", Duration.ofDays(30));
            return reminder;
        } catch (Exception e) {
            log.warn("[remind] 预约提醒生成失败: {}", e.getMessage());
            return null;
        }
    }

    public String checkWeatherReminder(Long userId, Long tripId) {
        Trip trip = tripService.detailAsMember(userId, tripId);
        String city = trip.getCity();
        if (city == null || city.isBlank()) {
            return null;
        }
        Map<String, Object> weather = weatherService.weather(city);
        String rainDate = findRainDate(weather);
        if (rainDate == null) {
            return null;
        }
        String key = "remind:weather:" + userId + ":" + tripId + ":" + rainDate;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return null;
        }
        String userPrompt = "目的地 " + city + " 天气：" + describeWeather(weather, rainDate)
                + "\n请提醒用户注意天气。";
        try {
            JsonNode result = deepSeekService.generateJson(REMINDER_SYSTEM_PROMPT, userPrompt);
            String reminder = result.path("reminder").asText();
            if (reminder == null || reminder.isBlank()) {
                return null;
            }
            redisTemplate.opsForValue().set(key, "1", Duration.ofDays(1));
            return reminder;
        } catch (Exception e) {
            log.warn("[remind] 天气提醒生成失败: {}", e.getMessage());
            return null;
        }
    }

    private List<String> collectBookingSpots(String result) {
        List<String> spots = new ArrayList<>();
        if (result == null || result.isBlank()) {
            return spots;
        }
        try {
            JsonNode guide = jsonMapper.readTree(result);
            for (JsonNode day : guide.path("days")) {
                for (JsonNode spot : day.path("spots")) {
                    String booking = spot.path("practical").path("booking").asText();
                    if (booking != null && (booking.contains("预约") || booking.contains("预定") || booking.contains("需提前"))) {
                        String name = spot.path("name").asText();
                        if (name != null && !name.isBlank()) {
                            spots.add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[remind] 解析预约信息失败: {}", e.getMessage());
        }
        return spots;
    }

    private String findRainDate(Map<String, Object> weather) {
        if (weather == null) {
            return null;
        }
        Object forecastObj = weather.get("forecast");
        if (!(forecastObj instanceof List<?> forecast)) {
            return null;
        }
        for (Object item : forecast) {
            if (item instanceof Map<?, ?> day) {
                String text = String.valueOf(day.get("text"));
                if (text != null && text.contains("雨")) {
                    return String.valueOf(day.get("date"));
                }
            }
        }
        return null;
    }

    private String describeWeather(Map<String, Object> weather, String rainDate) {
        StringBuilder sb = new StringBuilder();
        Object nowObj = weather.get("now");
        if (nowObj instanceof Map<?, ?> now) {
            sb.append("当前 ").append(now.get("text")).append(" ").append(now.get("temp")).append("°C；");
        }
        sb.append(rainDate).append(" 有雨。");
        return sb.toString();
    }
}
