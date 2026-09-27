package com.study.travel_guide.dto;

import lombok.Data;

@Data
public class ReorderFromRequest {
    private Double lat;
    private Double lng;
    private Integer dayIndex;
}
