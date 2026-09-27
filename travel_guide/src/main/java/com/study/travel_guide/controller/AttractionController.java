package com.study.travel_guide.controller;

import com.study.travel_guide.common.Result;
import com.study.travel_guide.dto.PopularAttraction;
import com.study.travel_guide.service.PopularAttractionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/attractions")
public class AttractionController {

    private final PopularAttractionService popularAttractionService;

    public AttractionController(PopularAttractionService popularAttractionService) {
        this.popularAttractionService = popularAttractionService;
    }

    @GetMapping("/popular")
    public Result<List<PopularAttraction>> popular(@RequestParam String city) {
        return Result.ok(popularAttractionService.popular(city));
    }
}
