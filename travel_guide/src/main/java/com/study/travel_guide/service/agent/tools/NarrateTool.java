package com.study.travel_guide.service.agent.tools;

import com.study.travel_guide.service.agent.Tool;
import com.study.travel_guide.service.guide.TourGuideService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class NarrateTool implements Tool {

    private final TourGuideService tourGuideService;

    public NarrateTool(TourGuideService tourGuideService) {
        this.tourGuideService = tourGuideService;
    }

    @Override
    public String name() {
        return "narrate";
    }

    @Override
    public String description() {
        return "生成景点的语音讲解词（用户想听景点讲解时调用）";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "attraction", Map.of("type", "string", "description", "景点名")
                ),
                "required", List.of("attraction")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String attraction = (String) args.get("attraction");
        if (attraction == null || attraction.isBlank()) {
            return "未提供景点名";
        }
        Map<String, Object> result = tourGuideService.narrate(attraction);
        Object script = result.get("script");
        return script == null ? "讲解生成失败" : script.toString();
    }
}
