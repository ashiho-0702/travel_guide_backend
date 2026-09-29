package com.study.travel_guide.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TripSummary {
    private Long id;
    private String title;
    private String city;
    private Integer days;
    private String startDate;
    private Boolean isFavorite;
    private Boolean isOwner;
    private LocalDateTime createdAt;
}
