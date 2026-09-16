package com.study.travel_guide.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Trip {
    private Long id;
    private Long userId;
    private String city;
    private String preferences;
    private String budget;
    private Integer days;
    private String energyLevel;
    private String extra;
    private String status;
    private String result;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
