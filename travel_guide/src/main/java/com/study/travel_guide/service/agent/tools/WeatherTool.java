package com.study.travel_guide.service.agent.tools;

import tools.jackson.databind.json.JsonMapper;
import com.study.travel_guide.service.WeatherService;
import com.study.travel_guide.service.agent.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class WeatherTool implements Tool {

    private final WeatherService weatherService;
    private final JsonMapper jsonMapper;

    public WeatherTool(WeatherService weatherService, JsonMapper jsonMapper) {
        this.weatherService = weatherService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String name() {
        return "get_weather";
    }

    @Override
    public String description() {
        return "查询某城市的天气（当前实况 + 未来 3 天预报）";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "city", Map.of("type", "string", "description", "城市名")
                ),
                "required", List.of("city")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String city = (String) args.get("city");
        if (city == null || city.isBlank()) {
            return "未提供城市名";
        }
        Map<String, Object> weather = weatherService.weather(city);
        try {
            return jsonMapper.writeValueAsString(weather);
        } catch (Exception e) {
            return "天气查询失败";
        }
    }
}
