package com.study.travel_guide.controller;

import com.study.travel_guide.common.BizException;
import com.study.travel_guide.common.Result;
import com.study.travel_guide.entity.User;
import com.study.travel_guide.mapper.PointFlowMapper;
import com.study.travel_guide.mapper.TripMapper;
import com.study.travel_guide.mapper.UserMapper;
import com.study.travel_guide.service.PopularAttractionService;
import com.study.travel_guide.service.guide.SeedAttractionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SeedAttractionService seedAttractionService;
    private final UserMapper userMapper;
    private final TripMapper tripMapper;
    private final PointFlowMapper pointFlowMapper;
    private final PopularAttractionService popularAttractionService;
    private final ExecutorService taskExecutor;

    public AdminController(SeedAttractionService seedAttractionService, UserMapper userMapper,
                           TripMapper tripMapper, PointFlowMapper pointFlowMapper,
                           PopularAttractionService popularAttractionService, ExecutorService taskExecutor) {
        this.seedAttractionService = seedAttractionService;
        this.userMapper = userMapper;
        this.tripMapper = tripMapper;
        this.pointFlowMapper = pointFlowMapper;
        this.popularAttractionService = popularAttractionService;
        this.taskExecutor = taskExecutor;
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

    @PostMapping("/warm-attractions")
    public Result<String> warmAttractions(@RequestAttribute("userId") Long userId) {
        requireAdmin(userId);
        taskExecutor.execute(() -> {
            int count = popularAttractionService.warmUp();
            log.info("[warm] 热门景点照片预热完成，共配到 {} 张", count);
        });
        return Result.ok("预热已启动");
    }

    private void requireAdmin(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getIsAdmin())) {
            throw new BizException(403, "无权限");
        }
    }
}
