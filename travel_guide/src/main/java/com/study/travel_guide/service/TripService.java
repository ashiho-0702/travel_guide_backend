package com.study.travel_guide.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.study.travel_guide.common.BizException;
import com.study.travel_guide.dto.TripSummary;
import com.study.travel_guide.entity.Trip;
import com.study.travel_guide.mapper.TripMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TripService {

    private final TripMapper tripMapper;
    private final JsonMapper jsonMapper;

    public TripService(TripMapper tripMapper, JsonMapper jsonMapper) {
        this.tripMapper = tripMapper;
        this.jsonMapper = jsonMapper;
    }

    public Trip detail(Long userId, Long id) {
        Trip trip = tripMapper.findByIdAndUser(id, userId);
        if (trip == null) {
            throw new BizException(404, "行程不存在");
        }
        return trip;
    }

    public List<TripSummary> list(Long userId, Boolean favorite) {
        return tripMapper.listSummaries(userId, favorite);
    }

    public void favorite(Long userId, Long id) {
        detail(userId, id);
        tripMapper.updateFavorite(id, userId, true);
    }

    public void unfavorite(Long userId, Long id) {
        detail(userId, id);
        tripMapper.updateFavorite(id, userId, false);
    }

    public void delete(Long userId, Long id) {
        int affected = tripMapper.deleteByIdAndUser(id, userId);
        if (affected == 0) {
            throw new BizException(404, "行程不存在");
        }
    }

    public String ensureShareToken(Long userId, Long id) {
        Trip trip = detail(userId, id);
        if (trip.getShareToken() != null && !trip.getShareToken().isBlank()) {
            return trip.getShareToken();
        }
        // 16 位 token：scene = "token=" + 16 = 22 字符，满足微信 scene 上限 32 字符
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        tripMapper.updateShareToken(id, userId, token);
        return token;
    }

    public Trip getByShareToken(String token) {
        Trip trip = tripMapper.findByShareToken(token);
        if (trip == null) {
            throw new BizException(404, "分享不存在或已失效");
        }
        return trip;
    }

    public void revokeShare(Long userId, Long id) {
        detail(userId, id);
        tripMapper.clearShareToken(id, userId);
    }

    // ===== 行程编辑（手动编辑/拖拽排序）=====

    public void addSpot(Long userId, Long id, int dayIndex, JsonNode spot) {
        ObjectNode root = loadResult(userId, id);
        getDay(root, dayIndex).withArray("spots").add(spot);
        recalculateCost(root);
        saveResult(userId, id, root);
    }

    public void editSpot(Long userId, Long id, int dayIndex, int spotIndex, JsonNode spot) {
        ObjectNode root = loadResult(userId, id);
        ArrayNode spots = getSpots(root, dayIndex);
        if (spotIndex < 0 || spotIndex >= spots.size()) {
            throw new BizException(400, "景点索引越界");
        }
        spots.set(spotIndex, spot);
        recalculateCost(root);
        saveResult(userId, id, root);
    }

    public void deleteSpot(Long userId, Long id, int dayIndex, int spotIndex) {
        ObjectNode root = loadResult(userId, id);
        ArrayNode spots = getSpots(root, dayIndex);
        if (spotIndex < 0 || spotIndex >= spots.size()) {
            throw new BizException(400, "景点索引越界");
        }
        spots.remove(spotIndex);
        recalculateCost(root);
        saveResult(userId, id, root);
    }

    public void moveSpot(Long userId, Long id, int fromDay, int fromSpot, int toDay, int toSpot) {
        ObjectNode root = loadResult(userId, id);
        ArrayNode days = (ArrayNode) root.get("days");
        if (days == null || fromDay < 0 || fromDay >= days.size() || toDay < 0 || toDay >= days.size()) {
            throw new BizException(400, "天数索引越界");
        }
        ArrayNode fromSpots = ((ObjectNode) days.get(fromDay)).withArray("spots");
        ArrayNode toSpots = ((ObjectNode) days.get(toDay)).withArray("spots");
        if (fromSpot < 0 || fromSpot >= fromSpots.size()) {
            throw new BizException(400, "源景点索引越界");
        }
        JsonNode moved = fromSpots.remove(fromSpot);
        int insertAt = toSpot;
        if (insertAt < 0) insertAt = 0;
        if (insertAt > toSpots.size()) insertAt = toSpots.size();
        toSpots.insert(insertAt, moved);
        recalculateCost(root);
        saveResult(userId, id, root);
    }

    public void updateResult(Long userId, Long id, JsonNode result) {
        detail(userId, id);
        tripMapper.updateResult(id, userId, result.toString());
    }

    private ObjectNode loadResult(Long userId, Long id) {
        Trip trip = detail(userId, id);
        try {
            return (ObjectNode) jsonMapper.readTree(trip.getResult());
        } catch (Exception e) {
            throw new BizException(500, "行程数据解析失败");
        }
    }

    private void saveResult(Long userId, Long id, ObjectNode root) {
        tripMapper.updateResult(id, userId, root.toString());
    }

    private ObjectNode getDay(ObjectNode root, int dayIndex) {
        ArrayNode days = (ArrayNode) root.get("days");
        if (days == null || dayIndex < 0 || dayIndex >= days.size()) {
            throw new BizException(400, "天数索引越界");
        }
        return (ObjectNode) days.get(dayIndex);
    }

    private ArrayNode getSpots(ObjectNode root, int dayIndex) {
        return getDay(root, dayIndex).withArray("spots");
    }

    private void recalculateCost(ObjectNode root) {
        ArrayNode days = (ArrayNode) root.get("days");
        if (days == null) {
            return;
        }
        int total = 0;
        for (JsonNode dayNode : days) {
            if (!(dayNode instanceof ObjectNode day)) {
                continue;
            }
            int dayTotal = 0;
            if (day.get("spots") instanceof ArrayNode spots) {
                for (JsonNode spot : spots) {
                    dayTotal += spot.path("estimatedCostCny").asInt(0);
                }
            }
            if (day.get("food") instanceof ArrayNode food) {
                for (JsonNode f : food) {
                    dayTotal += f.path("estimatedCostCny").asInt(0);
                }
            }
            day.put("estimatedCostCny", dayTotal);
            total += dayTotal;
        }
        root.put("estimatedTotalCost", total);
    }
}
