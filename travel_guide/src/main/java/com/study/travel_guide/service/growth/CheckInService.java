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
        LocalDate today = LocalDate.now();
        String todayKey = "growth:sign_in:" + userId + ":" + today;
        Boolean first = redisTemplate.opsForValue().setIfAbsent(todayKey, "1", Duration.ofHours(24));
        if (Boolean.FALSE.equals(first)) {
            throw new BizException("今日已签到");
        }

        int streak = calcStreak(userId, today);
        int bonus = GrowthRule.streakBonus(streak);
        int totalPoints = GrowthRule.SIGN_IN + bonus;

        String remark = bonus > 0 ? "每日签到（连续 " + streak + " 天）" : "每日签到";
        pointService.addPoints(userId, "sign_in", totalPoints, remark);

        Map<String, Object> data = pointService.summary(userId);
        data.put("todayPoints", totalPoints);
        data.put("streak", streak);
        return data;
    }

    private int calcStreak(Long userId, LocalDate today) {
        String streakKey = "growth:sign_in_streak:" + userId;
        String lastDateKey = "growth:sign_in_last:" + userId;
        String lastDate = redisTemplate.opsForValue().get(lastDateKey);
        int streak = 1;
        if (lastDate != null && lastDate.equals(today.minusDays(1).toString())) {
            String streakStr = redisTemplate.opsForValue().get(streakKey);
            streak = (streakStr == null ? 0 : Integer.parseInt(streakStr)) + 1;
        }
        redisTemplate.opsForValue().set(streakKey, String.valueOf(streak), Duration.ofDays(30));
        redisTemplate.opsForValue().set(lastDateKey, today.toString(), Duration.ofDays(30));
        return streak;
    }
}
