package com.study.travel_guide.dto;

import lombok.Data;

@Data
public class DeleteSpotRequest {
    private int dayIndex;
    private int spotIndex;
}
