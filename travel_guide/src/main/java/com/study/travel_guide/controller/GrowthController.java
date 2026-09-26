package com.study.travel_guide.controller;

import com.study.travel_guide.common.Result;
import com.study.travel_guide.entity.PointFlow;
import com.study.travel_guide.mapper.PointFlowMapper;
import com.study.travel_guide.service.growth.AchievementService;
import com.study.travel_guide.service.growth.CheckInService;
import com.study.travel_guide.service.growth.InviteService;
import com.study.travel_guide.service.growth.PointService;
import com.study.travel_guide.service.growth.RedeemService;
import com.study.travel_guide.service.growth.TripReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/growth")
public class GrowthController {

    private final CheckInService checkInService;
    private final PointService pointService;
    private final InviteService inviteService;
    private final RedeemService redeemService;
    private final AchievementService achievementService;
    private final PointFlowMapper pointFlowMapper;
    private final TripReportService tripReportService;

    public GrowthController(CheckInService checkInService, PointService pointService,
                            InviteService inviteService, RedeemService redeemService,
                            AchievementService achievementService, PointFlowMapper pointFlowMapper,
                            TripReportService tripReportService) {
        this.checkInService = checkInService;
        this.pointService = pointService;
        this.inviteService = inviteService;
        this.redeemService = redeemService;
        this.achievementService = achievementService;
        this.pointFlowMapper = pointFlowMapper;
        this.tripReportService = tripReportService;
    }

    @PostMapping("/sign-in")
    public Result<Map<String, Object>> signIn(@RequestAttribute("userId") Long userId) {
        return Result.ok(checkInService.signIn(userId));
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestAttribute("userId") Long userId) {
        return Result.ok(pointService.summary(userId));
    }

    @GetMapping("/points")
    public Result<List<PointFlow>> points(@RequestAttribute("userId") Long userId) {
        return Result.ok(pointFlowMapper.listByUser(userId, 20));
    }

    @GetMapping("/invite-info")
    public Result<Map<String, Object>> inviteInfo(@RequestAttribute("userId") Long userId) {
        return Result.ok(inviteService.inviteInfo(userId));
    }

    @GetMapping("/redeem-items")
    public Result<List<Map<String, Object>>> redeemItems() {
        return Result.ok(redeemService.redeemItems());
    }

    @PostMapping("/redeem")
    public Result<Map<String, Object>> redeem(@RequestAttribute("userId") Long userId,
                                              @RequestBody Map<String, String> body) {
        return Result.ok(redeemService.redeem(userId, body.get("itemId")));
    }

    @GetMapping("/achievements")
    public Result<List<Map<String, Object>>> achievements(@RequestAttribute("userId") Long userId) {
        return Result.ok(achievementService.achievements(userId));
    }

    @GetMapping("/report")
    public Result<Map<String, Object>> report(@RequestAttribute("userId") Long userId) {
        return Result.ok(tripReportService.report(userId));
    }
}
