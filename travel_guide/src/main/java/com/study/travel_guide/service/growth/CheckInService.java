package com.study.travel_guide.service.growth;

import com.study.travel_guide.common.BizException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

@Service
public class CheckInService {

    private final PointService pointService;
    private final StringRedisTemplate redisTemplate;

    public CheckInService(PointService pointService, StringRedisTemplate redisTemplate) {
        this.pointService = pointService;
        this.redisTemplate = redisTemplate;
    }

    public Map<String, Object> signIn(Long userId) {
        String key = "growth:sign_in:" + userId + ":" + LocalDate.now();
        Boolean first = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofHours(24));
        if (Boolean.FALSE.equals(first)) {
            throw new BizException("今日已签到");
        }
        pointService.addPoints(userId, "sign_in", GrowthRule.SIGN_IN, "每日签到");
        Map<String, Object> data = pointService.summary(userId);
        data.put("todayPoints", GrowthRule.SIGN_IN);
        return data;
    }
}
