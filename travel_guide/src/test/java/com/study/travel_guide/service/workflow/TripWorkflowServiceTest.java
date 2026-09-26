package com.study.travel_guide.service.workflow;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TripWorkflowServiceTest {

    @Test
    void parseStartDateValid() {
        assertEquals(LocalDate.of(2026, 9, 26), TripWorkflowService.parseStartDate("2026-09-26"));
    }

    @Test
    void parseStartDateInvalidReturnsNull() {
        assertNull(TripWorkflowService.parseStartDate(null));
        assertNull(TripWorkflowService.parseStartDate(""));
        assertNull(TripWorkflowService.parseStartDate("  "));
        assertNull(TripWorkflowService.parseStartDate("abc"));
    }

    @Test
    void seasonOfBoundaries() {
        assertEquals("冬季，可关注冰雪/温泉项目", TripWorkflowService.seasonOf(12));
        assertEquals("冬季，可关注冰雪/温泉项目", TripWorkflowService.seasonOf(1));
        assertEquals("冬季，可关注冰雪/温泉项目", TripWorkflowService.seasonOf(2));
        assertEquals("春季，可关注樱花/桃花等应季花卉", TripWorkflowService.seasonOf(3));
        assertEquals("春季，可关注樱花/桃花等应季花卉", TripWorkflowService.seasonOf(5));
        assertEquals("夏季，注意防晒避暑，可关注水上/避暑项目", TripWorkflowService.seasonOf(6));
        assertEquals("夏季，注意防晒避暑，可关注水上/避暑项目", TripWorkflowService.seasonOf(8));
        assertEquals("秋季，可关注红叶/银杏等应季景观", TripWorkflowService.seasonOf(9));
        assertEquals("秋季，可关注红叶/银杏等应季景观", TripWorkflowService.seasonOf(11));
    }
}
