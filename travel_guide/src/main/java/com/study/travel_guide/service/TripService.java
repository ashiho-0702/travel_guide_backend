package com.study.travel_guide.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.study.travel_guide.common.BizException;
import com.study.travel_guide.dto.RoutePoint;
import com.study.travel_guide.dto.TripSummary;
import com.study.travel_guide.entity.Trip;
import com.study.travel_guide.entity.User;
import com.study.travel_guide.mapper.TripCollaboratorMapper;
import com.study.travel_guide.mapper.TripMapper;
import com.study.travel_guide.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TripService {

    private final TripMapper tripMapper;
    private final JsonMapper jsonMapper;
    private final TripCollaboratorMapper tripCollaboratorMapper;
    private final UserMapper userMapper;
    private final RouteService routeService;

    public TripService(TripMapper tripMapper, JsonMapper jsonMapper,
                       TripCollaboratorMapper tripCollaboratorMapper, UserMapper userMapper,
                       RouteService routeService) {
        this.tripMapper = tripMapper;
        this.jsonMapper = jsonMapper;
        this.tripCollaboratorMapper = tripCollaboratorMapper;
        this.userMapper = userMapper;
        this.routeService = routeService;
    }

    public Trip detail(Long userId, Long id) {
        Trip trip = tripMapper.findByIdAndUser(id, userId);
        if (trip == null) {
            throw new BizException(404, "行程不存在");
        }
        return trip;
    }

    public Trip detailAsMember(Long userId, Long id) {
        Trip trip = tripMapper.findByIdAndMember(id, userId);
        if (trip == null) {
            throw new BizException(404, "行程不存在");
        }
        return trip;
    }

    public Long join(Long userId, String token) {
        if (token == null || token.isBlank()) {
            throw new BizException(400, "分享 token 不能为空");
        }
        Trip trip = tripMapper.findByShareToken(token);
        if (trip == null) {
            throw new BizException(404, "分享不存在或已失效");
        }
        if (!trip.getUserId().equals(userId) && tripCollaboratorMapper.exists(trip.getId(), userId) == 0) {
            tripCollaboratorMapper.insert(trip.getId(), userId);
        }
        return trip.getId();
    }

    public List<Map<String, Object>> listCollaborators(Long userId, Long tripId) {
        Trip trip = detailAsMember(userId, tripId);
        List<Map<String, Object>> result = new ArrayList<>();
        User owner = userMapper.findById(trip.getUserId());
        result.add(member(trip.getUserId(), owner == null ? null : owner.getNickname(),
                owner == null ? null : owner.getAvatarUrl(), true));
        for (Map<String, Object> c : tripCollaboratorMapper.listByTrip(tripId)) {
            c.put("isOwner", false);
            result.add(c);
        }
        return result;
    }

    public void removeCollaborator(Long ownerId, Long tripId, Long targetUserId) {
        Trip trip = detail(ownerId, tripId);
        if (trip.getUserId().equals(targetUserId)) {
            throw new BizException(400, "不能移除行程创建者");
        }
        tripCollaboratorMapper.delete(tripId, targetUserId);
    }

    private Map<String, Object> member(Long userId, String nickname, String avatarUrl, boolean isOwner) {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", userId);
        m.put("nickname", nickname);
        m.put("avatarUrl", avatarUrl);
        m.put("isOwner", isOwner);
        return m;
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
        saveResult(id, root);
    }

    public void editSpot(Long userId, Long id, int dayIndex, int spotIndex, JsonNode spot) {
        ObjectNode root = loadResult(userId, id);
        ArrayNode spots = getSpots(root, dayIndex);
        if (spotIndex < 0 || spotIndex >= spots.size()) {
            throw new BizException(400, "景点索引越界");
        }
        spots.set(spotIndex, spot);
        recalculateCost(root);
        saveResult(id, root);
    }

    public void deleteSpot(Long userId, Long id, int dayIndex, int spotIndex) {
        ObjectNode root = loadResult(userId, id);
        ArrayNode spots = getSpots(root, dayIndex);
        if (spotIndex < 0 || spotIndex >= spots.size()) {
            throw new BizException(400, "景点索引越界");
        }
        spots.remove(spotIndex);
        recalculateCost(root);
        saveResult(id, root);
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
        saveResult(id, root);
    }

    public void updateResult(Long userId, Long id, JsonNode result) {
        detailAsMember(userId, id);
        tripMapper.updateResultById(id, result.toString());
    }

    private ObjectNode loadResult(Long userId, Long id) {
        Trip trip = detailAsMember(userId, id);
        try {
            return (ObjectNode) jsonMapper.readTree(trip.getResult());
        } catch (Exception e) {
            throw new BizException(500, "行程数据解析失败");
        }
    }

    private void saveResult(Long id, ObjectNode root) {
        tripMapper.updateResultById(id, root.toString());
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

    /**
     * 从当前位置出发重排某天景点（委托 RouteService 做最近邻 + 路线距离）。
     */
    public Map<String, Object> reorderFromLocation(Long userId, Long tripId, double lat, double lng, int dayIndex) {
        ObjectNode root = loadResult(userId, tripId);
        ObjectNode day = getDay(root, dayIndex);
        ArrayNode spots = (ArrayNode) day.get("spots");
        if (spots == null || spots.size() == 0) {
            throw new BizException(400, "当天没有景点");
        }

        RoutePoint origin = new RoutePoint();
        origin.setLat(lat);
        origin.setLng(lng);
        List<RoutePoint> points = new ArrayList<>();
        for (JsonNode s : spots) {
            if (s instanceof ObjectNode obj && obj.has("lat") && obj.has("lng")) {
                RoutePoint p = new RoutePoint();
                p.setName(obj.path("name").asText());
                p.setLat(obj.get("lat").asDouble());
                p.setLng(obj.get("lng").asDouble());
                points.add(p);
            }
        }

        Map<String, Object> result = routeService.optimize(origin, points);
        result.put("dayIndex", dayIndex);
        return result;
    }
}
