package com.study.travel_guide.service.growth;

import com.study.travel_guide.entity.User;
import com.study.travel_guide.mapper.UserMapper;
import com.study.travel_guide.service.DeepSeekService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripReportServiceTest {

    private static final long USER_ID = 1L;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final UserMapper userMapper = mock(UserMapper.class);
    private final AchievementService achievementService = mock(AchievementService.class);
    private final DeepSeekService deepSeekService = mock(DeepSeekService.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);

    private TripReportService service() {
        when(redisTemplate.opsForValue()).thenReturn(ops);
        return new TripReportService(userMapper, achievementService, deepSeekService, redisTemplate);
    }

    private static Map<String, Object> ach(String id, int progress, int current) {
        return Map.of("id", id, "progress", progress, "current", current);
    }

    @Test
    void cacheHitReturnsCachedReportWithoutCallingAi() {
        when(ops.get(anyString())).thenReturn("缓存的报告");

        Map<String, Object> result = service().report(USER_ID);

        assertEquals("缓存的报告", result.get("report"));
        assertFalse(((String) result.get("date")).isBlank());
        verify(deepSeekService, never()).generateJson(anyString(), anyString());
    }

    @Test
    void generateCallsAiAndCaches() throws Exception {
        when(ops.get(anyString())).thenReturn(null);
        User user = new User();
        user.setNickname("小明");
        user.setGrowth(120);
        when(userMapper.findById(USER_ID)).thenReturn(user);
        when(achievementService.achievements(USER_ID)).thenReturn(List.of(
                ach("guide", 3, 1),
                ach("checkin", 2, 1),
                ach("streak", 0, 0),
                ach("invite", 1, 1),
                ach("city", 2, 0),
                ach("favorite", 5, 1)
        ));
        JsonNode node = JSON.readTree("{\"report\":\"AI生成的报告\"}");
        when(deepSeekService.generateJson(anyString(), anyString())).thenReturn(node);

        Map<String, Object> result = service().report(USER_ID);

        assertEquals("AI生成的报告", result.get("report"));
        verify(ops).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void fallbackWhenAiFails() {
        when(ops.get(anyString())).thenReturn(null);
        User user = new User();
        user.setNickname("小明");
        user.setGrowth(120);
        when(userMapper.findById(USER_ID)).thenReturn(user);
        when(achievementService.achievements(USER_ID)).thenReturn(List.of(
                ach("guide", 3, 1),
                ach("checkin", 2, 1),
                ach("streak", 0, 0),
                ach("invite", 1, 1),
                ach("city", 2, 0),
                ach("favorite", 5, 1)
        ));
        when(deepSeekService.generateJson(anyString(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        Map<String, Object> result = service().report(USER_ID);

        String report = (String) result.get("report");
        assertTrue(report.contains("小明"));
        assertTrue(report.contains("3 条行程"));
    }
}
