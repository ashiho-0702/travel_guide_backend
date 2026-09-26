package com.study.travel_guide.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.study.travel_guide.common.BizException;
import com.study.travel_guide.entity.Trip;
import com.study.travel_guide.service.wechat.ContentSecurityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 对话式行程重排（dry-run）：一句话指令在现有行程基础上局部调整，只返回预览不落库；
 * 落库由前端确认后调 PUT /api/trip/{id}/result。
 */
@Slf4j
@Service
public class TripReplanService {

    private static final String SYSTEM_PROMPT = "你是旅行行程重排助手。根据用户的一句话调整指令，在现有行程基础上做局部修改，"
            + "尽量保持未涉及的天、景点、美食、费用完全不变。只输出调整后的完整 JSON，结构与输入完全一致，不要增删字段。";

    private final TripService tripService;
    private final DeepSeekService deepSeekService;
    private final TencentMapService tencentMapService;
    private final JsonMapper jsonMapper;
    private final ContentSecurityService contentSecurityService;

    public TripReplanService(TripService tripService, DeepSeekService deepSeekService,
                             TencentMapService tencentMapService, JsonMapper jsonMapper,
                             ContentSecurityService contentSecurityService) {
        this.tripService = tripService;
        this.deepSeekService = deepSeekService;
        this.tencentMapService = tencentMapService;
        this.jsonMapper = jsonMapper;
        this.contentSecurityService = contentSecurityService;
    }

    public JsonNode replan(Long userId, Long tripId, String instruction) {
        long total = System.currentTimeMillis();
        log.info("[replan] 开始重排: userId={}, tripId={}, instruction={}", userId, tripId, instruction);
        if (instruction == null || instruction.isBlank()) {
            throw new BizException(400, "指令不能为空");
        }
        contentSecurityService.checkText(userId, instruction, ContentSecurityService.SCENE_COMMENT);

        long t = System.currentTimeMillis();
        Trip trip = tripService.detail(userId, tripId);
        JsonNode current;
        try {
            current = jsonMapper.readTree(trip.getResult());
        } catch (Exception e) {
            throw new BizException(500, "行程数据解析失败");
        }
        log.info("[replan] 1/3 载入行程 完成，耗时 {}ms", System.currentTimeMillis() - t);

        t = System.currentTimeMillis();
        String userPrompt = "现有行程 JSON：\n" + current + "\n\n用户调整指令：" + instruction
                + "\n\n请输出调整后的完整 JSON（结构不变），只改指令涉及部分，其余原样保留。";
        JsonNode updated = deepSeekService.generateJson(SYSTEM_PROMPT, userPrompt);
        log.info("[replan] 2/3 DeepSeek 重排 完成，耗时 {}ms", System.currentTimeMillis() - t);

        t = System.currentTimeMillis();
        preserveCoordinates(current, updated, trip.getCity());
        log.info("[replan] 3/3 坐标回填 完成，耗时 {}ms", System.currentTimeMillis() - t);

        log.info("[replan] 重排预览完成（dry-run，未落库），总耗时 {}ms, tripId={}", System.currentTimeMillis() - total, tripId);
        return updated;
    }

    private void preserveCoordinates(JsonNode oldRoot, JsonNode newRoot, String city) {
        Map<String, double[]> oldCoords = collectCoords(oldRoot);
        if (!(newRoot.get("days") instanceof ArrayNode days)) {
            return;
        }
        long lastRequestAt = 0;
        for (JsonNode day : days) {
            if (!(day.get("spots") instanceof ArrayNode spots)) {
                continue;
            }
            for (JsonNode spot : spots) {
                if (!(spot instanceof ObjectNode obj)) {
                    continue;
                }
                String name = obj.path("name").asText();
                if (name.isBlank()) {
                    continue;
                }
                double[] coord = oldCoords.get(normalize(name));
                if (coord != null) {
                    obj.put("lat", coord[0]);
                    obj.put("lng", coord[1]);
                    continue;
                }
                // 新景点：地理编码（腾讯个人 key QPS=1，间隔 ≥1.2s）
                long wait = 1200 - (System.currentTimeMillis() - lastRequestAt);
                if (wait > 0) {
                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                lastRequestAt = System.currentTimeMillis();
                double[] c = tencentMapService.searchLocation(name, city);
                if (c != null) {
                    obj.put("lat", c[0]);
                    obj.put("lng", c[1]);
                }
            }
        }
    }

    private Map<String, double[]> collectCoords(JsonNode root) {
        Map<String, double[]> map = new HashMap<>();
        if (root.get("days") instanceof ArrayNode days) {
            for (JsonNode day : days) {
                if (day.get("spots") instanceof ArrayNode spots) {
                    for (JsonNode spot : spots) {
                        if (spot instanceof ObjectNode obj && obj.has("lat") && obj.has("lng")) {
                            String name = obj.path("name").asText();
                            if (!name.isBlank()) {
                                map.put(normalize(name), new double[]{obj.get("lat").asDouble(), obj.get("lng").asDouble()});
                            }
                        }
                    }
                }
            }
        }
        return map;
    }

    private String normalize(String name) {
        return name.trim().toLowerCase();
    }
}
