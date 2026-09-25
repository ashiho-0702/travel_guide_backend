package com.study.travel_guide.dto;

import lombok.Data;

@Data
public class MoveSpotRequest {
    private int fromDay;
    private int fromSpot;
    private int toDay;
    private int toSpot;
}
