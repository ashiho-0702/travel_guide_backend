package com.study.travel_guide.dto;

import lombok.Data;

@Data
public class IdentifyRequest {
    private Double lat;
    private Double lng;
    private String image;
}
