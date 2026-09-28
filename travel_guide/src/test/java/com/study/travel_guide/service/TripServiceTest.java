package com.study.travel_guide.service;

import com.study.travel_guide.common.BizException;
import com.study.travel_guide.entity.Trip;
import com.study.travel_guide.mapper.TripCollaboratorMapper;
import com.study.travel_guide.mapper.TripMapper;
import com.study.travel_guide.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripServiceTest {

    private final TripMapper tripMapper = mock(TripMapper.class);
    private final TripCollaboratorMapper collaboratorMapper = mock(TripCollaboratorMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final RouteService routeService = mock(RouteService.class);

    private final TripService service = new TripService(tripMapper, jsonMapper, collaboratorMapper, userMapper, routeService);

    private Trip trip(long id, long ownerId) {
        Trip t = new Trip();
        t.setId(id);
        t.setUserId(ownerId);
        return t;
    }

    @Test
    void joinInsertsCollaboratorForNonOwner() {
        when(tripMapper.findByShareToken("tok")).thenReturn(trip(10L, 1L));
        when(collaboratorMapper.exists(10L, 2L)).thenReturn(0);

        assertEquals(10L, service.join(2L, "tok"));
        verify(collaboratorMapper).insert(10L, 2L);
    }

    @Test
    void joinSkipsInsertForOwner() {
        when(tripMapper.findByShareToken("tok")).thenReturn(trip(10L, 1L));

        assertEquals(10L, service.join(1L, "tok"));
        verify(collaboratorMapper, never()).insert(anyLong(), anyLong());
    }

    @Test
    void removeCollaboratorCannotRemoveOwner() {
        when(tripMapper.findByIdAndUser(10L, 1L)).thenReturn(trip(10L, 1L));

        assertThrows(BizException.class, () -> service.removeCollaborator(1L, 10L, 1L));
    }
}
