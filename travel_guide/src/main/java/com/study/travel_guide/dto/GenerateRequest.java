package com.study.travel_guide.dto;

import lombok.Data;

import java.util.List;

@Data
public class GenerateRequest {
    private String city;
    private String startDate;
    private Integer days;
    private Integer peopleCount;
    private String budget;
    private List<String> preferences;
    private String energyLevel;
    private List<String> transportation;
    private String extraRequirements;
}
