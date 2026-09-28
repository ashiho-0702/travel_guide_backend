package com.study.travel_guide.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeatherServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private WeatherService service(RestClient restClient) {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        return new WeatherService(restClient, JsonMapper.builder().build(), redisTemplate);
    }

    @Test
    void forecastReturnsNullWhenApiFails() {
        RestClient restClient = mock(RestClient.class);
        when(restClient.get()).thenThrow(new RuntimeException("network down"));

        assertNull(service(restClient).forecast("北京"));
    }

    @Test
    void weatherReturnsCityOnlyWhenApiFails() {
        RestClient restClient = mock(RestClient.class);
        when(restClient.get()).thenThrow(new RuntimeException("network down"));

        Map<String, Object> result = service(restClient).weather("杭州");

        assertEquals("杭州", result.get("city"));
        assertNull(result.get("now"));
        assertNull(result.get("forecast"));
    }
}
