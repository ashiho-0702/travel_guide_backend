package com.study.travel_guide.dto;

import lombok.Data;
import tools.jackson.databind.JsonNode;

@Data
public class EditSpotRequest {
    private int dayIndex;
    private int spotIndex;
    private JsonNode spot;
}
