package com.study.travel_guide.dto;

import lombok.Data;

import java.util.List;

@Data
public class RouteOptimizeRequest {
    private RoutePoint origin;
    private List<RoutePoint> points;
}
