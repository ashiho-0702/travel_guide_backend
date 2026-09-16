package com.study.travel_guide.service.agent.tools;

import com.study.travel_guide.service.TencentMapService;
import com.study.travel_guide.service.agent.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GeocodeTool implements Tool {

    private final TencentMapService tencentMapService;

    public GeocodeTool(TencentMapService tencentMapService) {
        this.tencentMapService = tencentMapService;
    }

    @Override
    public String name() {
        return "geocode";
    }

    @Override
    public String description() {
        return "查询景点的经纬度坐标";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "description", "景点名"),
                        "city", Map.of("type", "string", "description", "城市")
                ),
                "required", List.of("name", "city")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String name = (String) args.get("name");
        String city = (String) args.get("city");
        double[] coord = tencentMapService.searchLocation(name, city);
        return coord == null ? "未找到坐标" : "lat=" + coord[0] + ", lng=" + coord[1];
    }
}
