package com.study.travel_guide.dto;

import lombok.Data;

import java.util.List;

@Data
public class RouteOptimizeRequest {
    private List<RoutePoint> points;
}
