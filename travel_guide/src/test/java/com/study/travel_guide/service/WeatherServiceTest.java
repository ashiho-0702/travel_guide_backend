package com.study.travel_guide.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeatherServiceTest {

    @Test
    void forecastReturnsNullWhenApiFails() {
        RestClient restClient = mock(RestClient.class);
        when(restClient.get()).thenThrow(new RuntimeException("network down"));
        WeatherService ws = new WeatherService(restClient, JsonMapper.builder().build());

        assertNull(ws.forecast("北京"));
    }

    @Test
    void weatherReturnsCityOnlyWhenApiFails() {
        RestClient restClient = mock(RestClient.class);
        when(restClient.get()).thenThrow(new RuntimeException("network down"));
        WeatherService ws = new WeatherService(restClient, JsonMapper.builder().build());

        Map<String, Object> result = ws.weather("杭州");

        assertEquals("杭州", result.get("city"));
        assertNull(result.get("now"));
        assertNull(result.get("forecast"));
    }
}
