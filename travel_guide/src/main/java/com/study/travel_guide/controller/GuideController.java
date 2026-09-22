package com.study.travel_guide.controller;

import com.study.travel_guide.common.Result;
import com.study.travel_guide.dto.ChatRequest;
import com.study.travel_guide.dto.IdentifyRequest;
import com.study.travel_guide.dto.NarrateRequest;
import com.study.travel_guide.service.guide.AttractionIdentifyService;
import com.study.travel_guide.service.guide.ConversationService;
import com.study.travel_guide.service.guide.TourGuideService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/guide")
public class GuideController {

    private final AttractionIdentifyService identifyService;
    private final TourGuideService tourGuideService;
    private final ConversationService conversationService;

    public GuideController(AttractionIdentifyService identifyService,
                           TourGuideService tourGuideService,
                           ConversationService conversationService) {
        this.identifyService = identifyService;
        this.tourGuideService = tourGuideService;
        this.conversationService = conversationService;
    }

    @PostMapping("/identify")
    public Result<Map<String, Object>> identify(@RequestBody IdentifyRequest request) {
        return Result.ok(identifyService.identify(request.getLat(), request.getLng(), request.getImage()));
    }

    @PostMapping("/narrate")
    public Result<Map<String, Object>> narrate(@RequestBody NarrateRequest request) {
        return Result.ok(tourGuideService.narrate(request.getAttraction()));
    }

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody ChatRequest request) {
        return Result.ok(conversationService.chat(request.getSessionId(), request.getQuestion(), request.getAttraction()));
    }
}
