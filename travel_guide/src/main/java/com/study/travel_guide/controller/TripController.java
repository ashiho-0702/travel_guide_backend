package com.study.travel_guide.controller;

import com.study.travel_guide.common.Result;
import com.study.travel_guide.dto.GenerateRequest;
import com.study.travel_guide.dto.TripSummary;
import com.study.travel_guide.entity.Trip;
import com.study.travel_guide.service.TripService;
import com.study.travel_guide.service.wechat.QrCodeService;
import com.study.travel_guide.service.workflow.TripWorkflowService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Slf4j
@RestController
@RequestMapping("/api/trip")
public class TripController {

    private final TripService tripService;
    private final TripWorkflowService tripWorkflowService;
    private final QrCodeService qrCodeService;
    private final ExecutorService taskExecutor;

    public TripController(TripService tripService,
                          TripWorkflowService tripWorkflowService,
                          QrCodeService qrCodeService,
                          ExecutorService taskExecutor) {
        this.tripService = tripService;
        this.tripWorkflowService = tripWorkflowService;
        this.qrCodeService = qrCodeService;
        this.taskExecutor = taskExecutor;
    }

    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestAttribute("userId") Long userId,
                                                @RequestBody GenerateRequest request) {
        return Result.ok(tripWorkflowService.generate(userId, request));
    }

    @PostMapping("/generate/stream")
    public SseEmitter generateStream(@RequestAttribute("userId") Long userId,
                                     @RequestBody GenerateRequest request,
                                     HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = new SseEmitter(600_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(ex -> log.warn("SSE 连接错误: {}", ex.getMessage()));
        taskExecutor.execute(() -> tripWorkflowService.generateStreaming(userId, request, emitter));
        return emitter;
    }

    @GetMapping("/{id}")
    public Result<Trip> detail(@RequestAttribute("userId") Long userId,
                               @PathVariable Long id) {
        return Result.ok(tripService.detail(userId, id));
    }

    @GetMapping("/{id}/qrcode")
    public Result<Map<String, Object>> qrcode(@RequestAttribute("userId") Long userId,
                                              @PathVariable Long id) {
        String token = tripService.ensureShareToken(userId, id);
        byte[] image = qrCodeService.generateTripQrCode(token);
        return Result.ok(Map.of("image", Base64.getEncoder().encodeToString(image),
                "contentType", "image/png", "token", token));
    }

    @PostMapping("/{id}/share")
    public Result<Map<String, Object>> share(@RequestAttribute("userId") Long userId,
                                             @PathVariable Long id) {
        String token = tripService.ensureShareToken(userId, id);
        return Result.ok(Map.of("token", token));
    }

    @DeleteMapping("/{id}/share")
    public Result<Void> revokeShare(@RequestAttribute("userId") Long userId,
                                    @PathVariable Long id) {
        tripService.revokeShare(userId, id);
        return Result.ok();
    }

    @GetMapping("/public/{token}")
    public Result<Map<String, Object>> publicView(@PathVariable String token) {
        Trip trip = tripService.getByShareToken(token);
        Map<String, Object> data = new HashMap<>();
        data.put("id", trip.getId());
        data.put("city", trip.getCity());
        data.put("days", trip.getDays());
        data.put("result", trip.getResult());
        return Result.ok(data);
    }

    @GetMapping("/list")
    public Result<List<TripSummary>> list(@RequestAttribute("userId") Long userId) {
        return Result.ok(tripService.list(userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@RequestAttribute("userId") Long userId,
                               @PathVariable Long id) {
        tripService.delete(userId, id);
        return Result.ok();
    }
}
