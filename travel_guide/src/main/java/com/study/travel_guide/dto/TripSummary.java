package com.study.travel_guide.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TripSummary {
    private Long id;
    private String title;
    private Boolean isFavorite;
    private LocalDateTime createdAt;
}
