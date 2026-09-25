package com.study.travel_guide.service.growth;

import com.study.travel_guide.entity.User;
import com.study.travel_guide.mapper.PointFlowMapper;
import com.study.travel_guide.mapper.UserMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
public class PointService {

    private final UserMapper userMapper;
    private final PointFlowMapper pointFlowMapper;
    private final LevelService levelService;
    private final StringRedisTemplate redisTemplate;

    public PointService(UserMapper userMapper, PointFlowMapper pointFlowMapper, LevelService levelService,
                        StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.pointFlowMapper = pointFlowMapper;
        this.levelService = levelService;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public void addPoints(Long userId, String type, int points, String remark) {
        int growthDelta = Math.max(points, 0);
        int affected = userMapper.addGrowth(userId, points, growthDelta);
        if (affected == 0) {
            return;
        }
        pointFlowMapper.insert(userId, points, type, remark);
    }

    public Map<String, Object> summary(Long userId) {
        User user = userMapper.findById(userId);
        int points = user == null || user.getPoints() == null ? 0 : user.getPoints();
        int growth = user == null || user.getGrowth() == null ? 0 : user.getGrowth();

        Map<String, Object> data = new HashMap<>();
        data.put("points", points);
        data.put("growth", growth);
        data.putAll(levelService.levelInfo(growth));
        String todayKey = "growth:sign_in:" + userId + ":" + LocalDate.now();
        data.put("signedToday", Boolean.TRUE.equals(redisTemplate.hasKey(todayKey)));
        return data;
    }
}
