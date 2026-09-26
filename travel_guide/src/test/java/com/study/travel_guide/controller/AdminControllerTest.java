package com.study.travel_guide.controller;

import com.study.travel_guide.common.BizException;
import com.study.travel_guide.common.Result;
import com.study.travel_guide.entity.User;
import com.study.travel_guide.mapper.PointFlowMapper;
import com.study.travel_guide.mapper.TripMapper;
import com.study.travel_guide.mapper.UserMapper;
import com.study.travel_guide.service.guide.SeedAttractionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminControllerTest {

    private static final long USER_ID = 1L;

    private final UserMapper userMapper = mock(UserMapper.class);
    private final TripMapper tripMapper = mock(TripMapper.class);
    private final PointFlowMapper pointFlowMapper = mock(PointFlowMapper.class);
    private final SeedAttractionService seedAttractionService = mock(SeedAttractionService.class);

    private final AdminController controller =
            new AdminController(seedAttractionService, userMapper, tripMapper, pointFlowMapper);

    private User user(boolean admin) {
        User u = new User();
        u.setId(USER_ID);
        u.setIsAdmin(admin);
        return u;
    }

    @Test
    void statsRejectsNonAdmin() {
        when(userMapper.findById(USER_ID)).thenReturn(user(false));

        BizException e = assertThrows(BizException.class, () -> controller.stats(USER_ID));
        assertEquals(403, e.getCode());
    }

    @Test
    void statsReturnsDataForAdmin() {
        when(userMapper.findById(USER_ID)).thenReturn(user(true));
        when(userMapper.countAll()).thenReturn(10);
        when(tripMapper.countAll()).thenReturn(5);
        when(pointFlowMapper.countAllByType("check_in")).thenReturn(7);
        when(tripMapper.countDistinctUserToday()).thenReturn(3);
        when(tripMapper.topCities()).thenReturn(List.of(Map.of("city", "成都", "cnt", 42L)));

        Result<Map<String, Object>> result = controller.stats(USER_ID);

        assertEquals(0, result.getCode());
        assertEquals(10, result.getData().get("totalUsers"));
        assertEquals(5, result.getData().get("totalTrips"));
        assertEquals(7, result.getData().get("totalCheckIns"));
        assertEquals(3, result.getData().get("dau"));
    }

    @Test
    void seedAttractionsRejectsNonAdmin() {
        when(userMapper.findById(USER_ID)).thenReturn(user(false));

        BizException e = assertThrows(BizException.class, () -> controller.seedAttractions(USER_ID));
        assertEquals(403, e.getCode());
    }
}
