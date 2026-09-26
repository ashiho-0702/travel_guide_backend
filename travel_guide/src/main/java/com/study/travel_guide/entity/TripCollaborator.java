package com.study.travel_guide.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TripCollaborator {
    private Long id;
    private Long tripId;
    private Long userId;
    private LocalDateTime joinedAt;
}
