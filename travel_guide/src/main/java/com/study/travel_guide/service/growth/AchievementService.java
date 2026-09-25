package com.study.travel_guide.service.growth;

import com.study.travel_guide.mapper.InviteRelationMapper;
import com.study.travel_guide.mapper.PointFlowMapper;
import com.study.travel_guide.mapper.TripMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 成就勋章：达成特定行为解锁勋章，行为次数越多勋章越高阶（覆盖式，只显示当前最高级）。
 * 等级实时计算，不建表（进度都从现有数据统计）。
 */
@Service
public class AchievementService {

    private record AchievementLevel(int count, String title) {
    }

    private static final List<AchievementLevel> GUIDE_LEVELS = List.of(
            new AchievementLevel(1, "旅行启蒙"),
            new AchievementLevel(5, "行程规划师"),
            new AchievementLevel(15, "攻略大师"),
            new AchievementLevel(30, "环球策划师")
    );

    private static final List<AchievementLevel> CHECKIN_LEVELS = List.of(
            new AchievementLevel(1, "初探足迹"),
            new AchievementLevel(5, "足迹行者"),
            new AchievementLevel(15, "城市漫游者"),
            new AchievementLevel(30, "旅行收藏家")
    );

    private static final List<AchievementLevel> STREAK_LEVELS = List.of(
            new AchievementLevel(7, "七日之约"),
            new AchievementLevel(30, "月度坚守"),
            new AchievementLevel(100, "持之以恒")
    );

    private static final List<AchievementLevel> INVITE_LEVELS = List.of(
            new AchievementLevel(1, "引路人"),
            new AchievementLevel(5, "结伴而行"),
            new AchievementLevel(15, "旅友召集人")
    );

    private static final List<AchievementLevel> CITY_LEVELS = List.of(
            new AchievementLevel(3, "三城记"),
            new AchievementLevel(10, "十城游记"),
            new AchievementLevel(30, "城市猎人")
    );

    private static final List<AchievementLevel> FAVORITE_LEVELS = List.of(
            new AchievementLevel(5, "收藏初现"),
            new AchievementLevel(20, "攻略收藏家")
    );

    private final TripMapper tripMapper;
    private final PointFlowMapper pointFlowMapper;
    private final InviteRelationMapper inviteRelationMapper;
    private final StringRedisTemplate redisTemplate;

    public AchievementService(TripMapper tripMapper, PointFlowMapper pointFlowMapper,
                              InviteRelationMapper inviteRelationMapper, StringRedisTemplate redisTemplate) {
        this.tripMapper = tripMapper;
        this.pointFlowMapper = pointFlowMapper;
        this.inviteRelationMapper = inviteRelationMapper;
        this.redisTemplate = redisTemplate;
    }

    public List<Map<String, Object>> achievements(Long userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(build("guide", "攻略策划", tripMapper.countByUser(userId), GUIDE_LEVELS));
        result.add(build("checkin", "足迹打卡", pointFlowMapper.countByType(userId, "check_in"), CHECKIN_LEVELS));
        result.add(build("streak", "坚持不懈", currentStreak(userId), STREAK_LEVELS));
        result.add(build("invite", "结伴同行", inviteRelationMapper.countByInviter(userId), INVITE_LEVELS));
        result.add(build("city", "城市猎人", tripMapper.countDistinctCity(userId), CITY_LEVELS));
        result.add(build("favorite", "收藏家", tripMapper.countFavorite(userId), FAVORITE_LEVELS));
        return result;
    }

    private int currentStreak(Long userId) {
        String s = redisTemplate.opsForValue().get("growth:sign_in_streak:" + userId);
        return s == null ? 0 : Integer.parseInt(s);
    }

    private Map<String, Object> build(String id, String name, int progress, List<AchievementLevel> levels) {
        int current = 0;
        String title = "";
        for (int i = 0; i < levels.size(); i++) {
            if (progress >= levels.get(i).count()) {
                current = i + 1;
                title = levels.get(i).title();
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("name", name);
        data.put("progress", progress);
        data.put("current", current);
        data.put("title", title);
        List<Map<String, Object>> levelList = new ArrayList<>();
        for (AchievementLevel l : levels) {
            Map<String, Object> lm = new HashMap<>();
            lm.put("count", l.count());
            lm.put("title", l.title());
            levelList.add(lm);
        }
        data.put("levels", levelList);
        return data;
    }
}
