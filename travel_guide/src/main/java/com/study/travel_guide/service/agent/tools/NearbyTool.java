package com.study.travel_guide.service.agent.tools;

import com.study.travel_guide.service.TencentMapService;
import com.study.travel_guide.service.agent.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class NearbyTool implements Tool {

    private final TencentMapService tencentMapService;

    public NearbyTool(TencentMapService tencentMapService) {
        this.tencentMapService = tencentMapService;
    }

    @Override
    public String name() {
        return "search_nearby";
    }

    @Override
    public String description() {
        return "搜索用户当前位置附近的景点/美食/娱乐等 POI，用于推荐附近好玩的";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "lat", Map.of("type", "number", "description", "纬度"),
                        "lng", Map.of("type", "number", "description", "经度"),
                        "category", Map.of("type", "string", "description", "POI 分类，如「旅游景点」「美食」，可选")
                ),
                "required", List.of("lat", "lng")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        double lat = toDouble(args.get("lat"));
        double lng = toDouble(args.get("lng"));
        if (lat == 0 && lng == 0) {
            return "用户当前位置未知，无法推荐附近";
        }
        String category = (String) args.get("category");
        String result = tencentMapService.searchNearby(lat, lng, category);
        return result == null ? "附近未找到相关地点" : result;
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (Exception ignored) {
            }
        }
        return 0;
    }
}
