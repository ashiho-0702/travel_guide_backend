package com.study.travel_guide.controller;

import com.study.travel_guide.common.Result;
import com.study.travel_guide.service.guide.SeedAttractionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SeedAttractionService seedAttractionService;

    public AdminController(SeedAttractionService seedAttractionService) {
        this.seedAttractionService = seedAttractionService;
    }

    @PostMapping("/seed-attractions")
    public Result<Map<String, Object>> seedAttractions() {
        return Result.ok(seedAttractionService.seed());
    }
}
