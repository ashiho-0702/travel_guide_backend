package com.study.travel_guide.controller;

import com.study.travel_guide.common.BizException;
import com.study.travel_guide.common.Result;
import com.study.travel_guide.entity.User;
import com.study.travel_guide.mapper.PointFlowMapper;
import com.study.travel_guide.mapper.TripMapper;
import com.study.travel_guide.mapper.UserMapper;
import com.study.travel_guide.service.guide.SeedAttractionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SeedAttractionService seedAttractionService;
    private final UserMapper userMapper;
    private final TripMapper tripMapper;
    private final PointFlowMapper pointFlowMapper;

    public AdminController(SeedAttractionService seedAttractionService, UserMapper userMapper,
                           TripMapper tripMapper, PointFlowMapper pointFlowMapper) {
        this.seedAttractionService = seedAttractionService;
        this.userMapper = userMapper;
        this.tripMapper = tripMapper;
        this.pointFlowMapper = pointFlowMapper;
    }

    @PostMapping("/seed-attractions")
    public Result<Map<String, Object>> seedAttractions(@RequestAttribute("userId") Long userId) {
        requireAdmin(userId);
        return Result.ok(seedAttractionService.seed());
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestAttribute("userId") Long userId) {
        requireAdmin(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("totalUsers", userMapper.countAll());
        data.put("totalTrips", tripMapper.countAll());
        data.put("totalCheckIns", pointFlowMapper.countAllByType("check_in"));
        data.put("dau", tripMapper.countDistinctUserToday());
        data.put("topCities", tripMapper.topCities());
        return Result.ok(data);
    }

    private void requireAdmin(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getIsAdmin())) {
            throw new BizException(403, "无权限");
        }
    }
}
