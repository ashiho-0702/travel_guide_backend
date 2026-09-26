package com.study.travel_guide.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

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
}
