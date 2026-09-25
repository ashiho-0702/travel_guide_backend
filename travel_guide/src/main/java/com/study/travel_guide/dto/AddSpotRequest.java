package com.study.travel_guide.dto;

import lombok.Data;
import tools.jackson.databind.JsonNode;

@Data
public class AddSpotRequest {
    private int dayIndex;
    private JsonNode spot;
}
