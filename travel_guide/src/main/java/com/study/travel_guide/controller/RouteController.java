package com.study.travel_guide.controller;

import com.study.travel_guide.common.Result;
import com.study.travel_guide.dto.RouteOptimizeRequest;
import com.study.travel_guide.service.RouteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/route")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping("/optimize")
    public Result<Map<String, Object>> optimize(@RequestBody RouteOptimizeRequest request) {
        int pointCount = request.getPoints() == null ? 0 : request.getPoints().size();
        log.info("[route] 收到优化请求: {} 个点", pointCount);
        return Result.ok(routeService.optimize(null, request.getPoints()));
    }
}
