package com.study.travel_guide.service.growth;

import com.study.travel_guide.entity.User;
import com.study.travel_guide.mapper.UserMapper;
import com.study.travel_guide.service.DeepSeekService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 旅行报告（日度）：统计用户旅行数据，交给 DeepSeek 生成个性化总结，当天缓存。
 * 统计口径复用 {@link AchievementService}，避免重复查库。
 */
@Slf4j
@Service
public class TripReportService {

    // 与 MySQL 连接 serverTimezone 对齐，保证「每日零点更新」语义一致
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final UserMapper userMapper;
    private final AchievementService achievementService;
    private final DeepSeekService deepSeekService;
    private final StringRedisTemplate redisTemplate;

    public TripReportService(UserMapper userMapper, AchievementService achievementService,
                             DeepSeekService deepSeekService, StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.achievementService = achievementService;
        this.deepSeekService = deepSeekService;
        this.redisTemplate = redisTemplate;
    }

    public Map<String, Object> report(Long userId) {
        LocalDate today = LocalDate.now(ZONE);
        String cacheKey = "growth:report:" + userId + ":" + today;

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("report", cached);
            data.put("date", today.toString());
            return data;
        }

        String report = generate(userId);

        redisTemplate.opsForValue().set(cacheKey, report, Duration.ofSeconds(secondsUntilMidnight()));

        Map<String, Object> data = new HashMap<>();
        data.put("report", report);
        data.put("date", today.toString());
        return data;
    }

    private String generate(Long userId) {
        User user = userMapper.findById(userId);
        String nickname = user == null || user.getNickname() == null ? "旅行者" : user.getNickname();
        int growth = user == null || user.getGrowth() == null ? 0 : user.getGrowth();
        String levelTitle = GrowthRule.levelTitle(GrowthRule.levelOf(growth));

        // 一次成就统计拿到全部计数：guide/checkin/streak/invite/city/favorite 的 progress
        List<Map<String, Object>> achievements = achievementService.achievements(userId);
        Map<String, Integer> progress = new HashMap<>();
        int achievementCount = 0;
        for (Map<String, Object> a : achievements) {
            String id = (String) a.get("id");
            progress.put(id, ((Number) a.getOrDefault("progress", 0)).intValue());
            achievementCount += ((Number) a.getOrDefault("current", 0)).intValue();
        }
        int guideCount = progress.getOrDefault("guide", 0);
        int checkInCount = progress.getOrDefault("checkin", 0);
        int streak = progress.getOrDefault("streak", 0);
        int inviteCount = progress.getOrDefault("invite", 0);
        int cityCount = progress.getOrDefault("city", 0);
        int favoriteCount = progress.getOrDefault("favorite", 0);

        try {
            JsonNode node = deepSeekService.generateJson(
                    "你是亲切的旅行助手，擅长用温暖的语言总结用户的旅行足迹，并给出实用的旅行建议。",
                    buildPrompt(nickname, levelTitle, guideCount, cityCount, favoriteCount,
                            checkInCount, inviteCount, streak, achievementCount));
            String report = node.path("report").asText();
            if (report == null || report.isBlank()) {
                report = node.asText();
            }
            if (report != null && !report.isBlank()) {
                return report;
            }
        } catch (Exception e) {
            log.warn("report generate failed, fallback to template: {}", e.getMessage());
        }
        return "你好，" + nickname + "！你已经规划了 " + guideCount + " 条行程，足迹遍布 " + cityCount
                + " 座城市。继续保持探索的热情，下一站会更精彩！";
    }

    private String buildPrompt(String nickname, String levelTitle, int guideCount, int cityCount,
                               int favoriteCount, int checkInCount, int inviteCount, int streak,
                               int achievementCount) {
        return "用户昵称：" + nickname + "\n" +
                "等级称号：" + levelTitle + "\n" +
                "已生成攻略数：" + guideCount + "\n" +
                "足迹城市数：" + cityCount + "\n" +
                "收藏攻略数：" + favoriteCount + "\n" +
                "景点打卡数：" + checkInCount + "\n" +
                "邀请好友数：" + inviteCount + "\n" +
                "连续签到天数：" + streak + "\n" +
                "成就勋章数：" + achievementCount + "\n\n" +
                "请基于以上数据写一段 100-200 字的个性化旅行总结（称呼用户昵称），并给出一条旅行建议。\n" +
                "只返回 JSON，格式为：{\"report\": \"总结文字\"}";
    }

    private long secondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime midnight = LocalDate.now(ZONE).plusDays(1).atStartOfDay();
        long seconds = Duration.between(now, midnight).getSeconds();
        return Math.max(seconds, 60);
    }
}
