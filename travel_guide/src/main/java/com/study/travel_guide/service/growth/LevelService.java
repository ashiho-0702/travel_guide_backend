package com.study.travel_guide.service.growth;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class LevelService {

    public Map<String, Object> levelInfo(int growth) {
        int level = GrowthRule.levelOf(growth);
        Map<String, Object> data = new HashMap<>();
        data.put("level", level);
        data.put("title", GrowthRule.levelTitle(level));
        data.put("nextLevelGrowth", GrowthRule.nextLevelGrowth(growth));
        return data;
    }
}
