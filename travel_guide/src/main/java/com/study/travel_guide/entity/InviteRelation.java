package com.study.travel_guide.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InviteRelation {
    private Long id;
    private Long inviterId;
    private Long inviteeId;
    private LocalDateTime createdAt;
}
