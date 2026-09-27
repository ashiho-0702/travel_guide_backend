package com.study.travel_guide.service;

import tools.jackson.databind.JsonNode;
import com.study.travel_guide.common.BizException;
import com.study.travel_guide.entity.PackingItem;
import com.study.travel_guide.entity.Trip;
import com.study.travel_guide.mapper.PackingItemMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 行李清单：每个行程一份，AI 按行程生成初始清单，用户逐项增删改/勾选。
 */
@Slf4j
@Service
public class PackingService {

    private static final String SYSTEM_PROMPT = "你是旅行行李打包助手。根据行程信息，列出该带的东西清单，"
            + "覆盖证件、电子、衣物、洗漱、药品、其他。只输出 JSON：{\"items\": [\"...\", \"...\"]}。";

    private final TripService tripService;
    private final PackingItemMapper packingItemMapper;
    private final DeepSeekService deepSeekService;

    public PackingService(TripService tripService, PackingItemMapper packingItemMapper,
                          DeepSeekService deepSeekService) {
        this.tripService = tripService;
        this.packingItemMapper = packingItemMapper;
        this.deepSeekService = deepSeekService;
    }

    public List<PackingItem> list(Long userId, Long tripId) {
        tripService.detailAsMember(userId, tripId);
        return packingItemMapper.listByTrip(tripId);
    }

    public List<PackingItem> generate(Long userId, Long tripId) {
        Trip trip = tripService.detailAsMember(userId, tripId);
        JsonNode node = deepSeekService.generateJson(SYSTEM_PROMPT, buildPrompt(trip));
        List<String> items = new ArrayList<>();
        for (JsonNode it : node.path("items")) {
            String name = it.asText();
            if (name != null && !name.isBlank()) {
                items.add(name.trim());
            }
        }
        if (items.isEmpty()) {
            log.warn("[packing] AI 返回空清单，保留现有清单, tripId={}", tripId);
            return packingItemMapper.listByTrip(tripId);
        }
        packingItemMapper.deleteByTrip(tripId);
        for (String name : items) {
            packingItemMapper.insert(tripId, name, false);
        }
        log.info("[packing] 生成行李清单完成, tripId={}, 共 {} 项", tripId, items.size());
        return packingItemMapper.listByTrip(tripId);
    }

    public List<PackingItem> add(Long userId, Long tripId, String name) {
        tripService.detailAsMember(userId, tripId);
        if (name == null || name.isBlank()) {
            throw new BizException(400, "物品名不能为空");
        }
        packingItemMapper.insert(tripId, name.trim(), false);
        log.info("[packing] 新增物品: tripId={}, name={}", tripId, name.trim());
        return packingItemMapper.listByTrip(tripId);
    }

    public void update(Long userId, Long tripId, Long itemId, String name, Boolean checked) {
        tripService.detailAsMember(userId, tripId);
        if (name == null || name.isBlank()) {
            throw new BizException(400, "物品名不能为空");
        }
        int affected = packingItemMapper.update(itemId, tripId, name.trim(), Boolean.TRUE.equals(checked));
        if (affected == 0) {
            throw new BizException(404, "物品不存在");
        }
        log.info("[packing] 更新物品: tripId={}, itemId={}", tripId, itemId);
    }

    public void delete(Long userId, Long tripId, Long itemId) {
        tripService.detailAsMember(userId, tripId);
        packingItemMapper.delete(itemId, tripId);
        log.info("[packing] 删除物品: tripId={}, itemId={}", tripId, itemId);
    }

    private String buildPrompt(Trip trip) {
        StringBuilder sb = new StringBuilder();
        sb.append("目的地：").append(trip.getCity()).append('\n');
        if (trip.getStartDate() != null && !trip.getStartDate().isBlank()) {
            sb.append("开始日期：").append(trip.getStartDate()).append('\n');
        }
        sb.append("天数：").append(trip.getDays()).append("天\n");
        if (trip.getPeopleCount() != null) {
            sb.append("人数：").append(trip.getPeopleCount()).append("人\n");
        }
        sb.append("请列出需要带的物品清单（10-20 项）。只输出 JSON：{\"items\": [...]}");
        return sb.toString();
    }
}
