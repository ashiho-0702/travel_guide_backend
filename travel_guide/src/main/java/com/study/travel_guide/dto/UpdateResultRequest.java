package com.study.travel_guide.dto;

import lombok.Data;
import tools.jackson.databind.JsonNode;

@Data
public class UpdateResultRequest {
    private JsonNode result;
}
