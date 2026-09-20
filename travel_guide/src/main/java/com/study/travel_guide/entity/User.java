package com.study.travel_guide.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {
    private Long id;
    private String openid;
    private String nickname;
    private String avatarUrl;
    private Integer points;
    private Integer growth;
    private Integer level;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
