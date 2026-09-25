package com.study.travel_guide.service.growth;

import com.study.travel_guide.common.BizException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RedeemService {

    private final PointService pointService;

    public RedeemService(PointService pointService) {
        this.pointService = pointService;
    }

    public List<Map<String, Object>> redeemItems() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<String, Integer> e : GrowthRule.REDEEM_COSTS.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("itemId", e.getKey());
            item.put("name", GrowthRule.REDEEM_NAMES.getOrDefault(e.getKey(), e.getKey()));
            item.put("cost", e.getValue());
            items.add(item);
        }
        return items;
    }

    public Map<String, Object> redeem(Long userId, String itemId) {
        Integer cost = GrowthRule.REDEEM_COSTS.get(itemId);
        if (cost == null) {
            throw new BizException(400, "兑换项不存在");
        }
        Map<String, Object> summary = pointService.summary(userId);
        int points = summary.get("points") == null ? 0 : (int) summary.get("points");
        if (points < cost) {
            throw new BizException(400, "积分不足");
        }
        String name = GrowthRule.REDEEM_NAMES.getOrDefault(itemId, itemId);
        pointService.addPoints(userId, "redeem", -cost, "兑换：" + name);

        Map<String, Object> data = pointService.summary(userId);
        data.put("itemId", itemId);
        data.put("name", name);
        data.put("cost", cost);
        return data;
    }
}
