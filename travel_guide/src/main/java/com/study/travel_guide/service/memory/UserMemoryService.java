package com.study.travel_guide.service.memory;

import com.study.travel_guide.dto.TravelEnums;
import com.study.travel_guide.entity.Trip;
import com.study.travel_guide.mapper.TripMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class UserMemoryService {

    private final TripMapper tripMapper;

    public UserMemoryService(TripMapper tripMapper) {
        this.tripMapper = tripMapper;
    }

    public String buildMemoryContext(Long userId, int limit) {
        List<Trip> recent = tripMapper.findRecentByUser(userId, limit);
        if (recent == null || recent.isEmpty()) {
            return "";
        }

        List<String> cities = recent.stream()
                .map(Trip::getCity).filter(c -> c != null && !c.isBlank())
                .distinct().collect(Collectors.toList());
        List<String> prefs = recent.stream()
                .map(Trip::getPreferences)
                .filter(p -> p != null && !p.isBlank())
                .flatMap(p -> Arrays.stream(p.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(TravelEnums.PREFERENCE_LABELS::get)
                .filter(Objects::nonNull)
                .distinct().collect(Collectors.toList());
        List<String> energies = recent.stream()
                .map(Trip::getEnergyLevel).filter(e -> e != null && !e.isBlank())
                .distinct().collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("该用户的历史偏好信息：\n");
        if (!cities.isEmpty()) {
            sb.append("- 去过的城市：").append(String.join("、", cities)).append('\n');
        }
        if (!prefs.isEmpty()) {
            sb.append("- 偏好的景点类型：").append(String.join("、", prefs)).append('\n');
        }
        if (!energies.isEmpty()) {
            sb.append("- 常用体力档位：").append(String.join("、", energies)).append('\n');
        }
        return sb.toString();
    }
}
