package com.study.travel_guide.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String sessionId;
    private String question;
}
