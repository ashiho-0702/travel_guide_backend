package com.study.travel_guide.dto;

import lombok.Data;

@Data
public class GenerateRequest {
    private String city;
    private String preferences;
    private String budget;
    private Integer days;
    private String energyLevel;
    private String extra;
}
