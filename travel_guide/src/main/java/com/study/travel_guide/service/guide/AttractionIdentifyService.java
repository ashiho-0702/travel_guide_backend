package com.study.travel_guide.service.guide;

import com.study.travel_guide.common.BizException;
import com.study.travel_guide.service.TencentMapService;
import com.study.travel_guide.service.growth.GrowthRule;
import com.study.travel_guide.service.growth.PointService;
import com.study.travel_guide.service.qwen.QwenVlService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class AttractionIdentifyService {

    private final TencentMapService tencentMapService;
    private final QwenVlService qwenVlService;
    private final PointService pointService;
    private final StringRedisTemplate redisTemplate;

    public AttractionIdentifyService(TencentMapService tencentMapService,
                                     QwenVlService qwenVlService,
                                     PointService pointService,
                                     StringRedisTemplate redisTemplate) {
        this.tencentMapService = tencentMapService;
        this.qwenVlService = qwenVlService;
        this.pointService = pointService;
        this.redisTemplate = redisTemplate;
    }

    public Map<String, Object> identify(Long userId, Double lat, Double lng, String image) {
        // 优先定位
        if (lat != null && lng != null) {
            String name = tencentMapService.searchNearby(lat, lng);
            if (name != null && !name.isBlank()) {
                awardCheckIn(userId, name);
                Map<String, Object> result = new HashMap<>();
                result.put("attraction", name);
                result.put("source", "location");
                return result;
            }
        }
        // 拍照兜底
        if (image != null && !image.isBlank()) {
            String name = qwenVlService.identifyAttraction(image);
            if (name != null && !name.isBlank()) {
                awardCheckIn(userId, name);
                Map<String, Object> result = new HashMap<>();
                result.put("attraction", name);
                result.put("source", "image");
                return result;
            }
        }
        throw new BizException("未能识别景点，请尝试拍照");
    }

    private void awardCheckIn(Long userId, String attraction) {
        if (userId == null || attraction == null || attraction.isBlank()) {
            return;
        }
        // 防重复：同一景点当天只加一次
        String key = "growth:check_in:" + userId + ":" + attraction + ":" + LocalDate.now();
        Boolean first = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofHours(24));
        if (Boolean.FALSE.equals(first)) {
            return;
        }
        try {
            pointService.addPoints(userId, "check_in", GrowthRule.CHECK_IN, "景点打卡：" + attraction);
        } catch (Exception e) {
            log.warn("打卡加分失败: {}", e.getMessage());
        }
    }
}
