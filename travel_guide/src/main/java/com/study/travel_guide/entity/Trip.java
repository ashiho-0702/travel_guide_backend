package com.study.travel_guide.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Trip {
    private Long id;
    private Long userId;
    private String city;
    private String startDate;
    private String preferences;
    private String budget;
    private Integer days;
    private Integer peopleCount;
    private String energyLevel;
    private String transportation;
    private String extraRequirements;
    private String shareToken;
    private String status;
    private String result;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
