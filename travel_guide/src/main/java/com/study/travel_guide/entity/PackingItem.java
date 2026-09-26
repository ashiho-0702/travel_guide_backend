package com.study.travel_guide.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PackingItem {
    private Long id;
    private Long tripId;
    private String name;
    private Boolean checked;
    private LocalDateTime createdAt;
}
