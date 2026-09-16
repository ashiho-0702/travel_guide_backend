package com.study.travel_guide.controller;

import com.study.travel_guide.common.Result;
import com.study.travel_guide.dto.GenerateRequest;
import com.study.travel_guide.dto.TripSummary;
import com.study.travel_guide.entity.Trip;
import com.study.travel_guide.service.TripService;
import com.study.travel_guide.service.workflow.TripWorkflowService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/api/trip")
public class TripController {

    private final TripService tripService;
    private final TripWorkflowService tripWorkflowService;
    private final ExecutorService taskExecutor;

    public TripController(TripService tripService,
                          TripWorkflowService tripWorkflowService,
                          ExecutorService taskExecutor) {
        this.tripService = tripService;
        this.tripWorkflowService = tripWorkflowService;
        this.taskExecutor = taskExecutor;
    }

    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestAttribute("userId") Long userId,
                                                @RequestBody GenerateRequest request) {
        return Result.ok(tripWorkflowService.generate(userId, request));
    }

    @PostMapping("/generate/stream")
    public SseEmitter generateStream(@RequestAttribute("userId") Long userId,
                                     @RequestBody GenerateRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);
        taskExecutor.execute(() -> tripWorkflowService.generateStreaming(userId, request, emitter));
        return emitter;
    }

    @GetMapping("/{id}")
    public Result<Trip> detail(@RequestAttribute("userId") Long userId,
                               @PathVariable Long id) {
        return Result.ok(tripService.detail(userId, id));
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
