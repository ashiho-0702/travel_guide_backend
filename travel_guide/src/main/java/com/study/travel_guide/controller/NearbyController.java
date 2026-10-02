package com.study.travel_guide.controller;

import com.study.travel_guide.common.BizException;
import com.study.travel_guide.common.Result;
import com.study.travel_guide.service.TencentMapService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 周边设施推荐：根据定位查找最近的公厕/民宿/停车场/充电桩。
 */
@Slf4j
@RestController
@RequestMapping("/api/nearby")
public class NearbyController {

    private final TencentMapService tencentMapService;

    public NearbyController(TencentMapService tencentMapService) {
        this.tencentMapService = tencentMapService;
    }

    @GetMapping("/facilities")
    public Result<Map<String, Object>> facilities(@RequestParam double lat, @RequestParam double lng) {
        if (lat == 0 && lng == 0) {
            throw new BizException("定位信息无效，无法查找周边");
        }
        log.info("[nearby] 查询周边设施: lat={}, lng={}", lat, lng);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("公厕", tencentMapService.searchNearestPoi(lat, lng, "公厕"));
        data.put("民宿", tencentMapService.searchNearestPoi(lat, lng, "民宿"));
        data.put("停车场", tencentMapService.searchNearestPoi(lat, lng, "停车场"));
        data.put("充电桩", tencentMapService.searchNearestPoi(lat, lng, "充电站"));
        return Result.ok(data);
    }
}
