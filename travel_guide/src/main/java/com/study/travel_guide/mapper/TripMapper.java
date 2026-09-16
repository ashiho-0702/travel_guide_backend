package com.study.travel_guide.mapper;

import com.study.travel_guide.dto.TripSummary;
import com.study.travel_guide.entity.Trip;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TripMapper {

    @Insert("INSERT INTO trip(user_id, city, preferences, budget, days, energy_level, extra, status, result) " +
            "VALUES(#{userId}, #{city}, #{preferences}, #{budget}, #{days}, #{energyLevel}, #{extra}, #{status}, #{result})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Trip trip);

    @Select("SELECT * FROM trip WHERE id = #{id} AND user_id = #{userId}")
    Trip findByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    @Select("SELECT id, CONCAT(city, ' ', days, '天') AS title, created_at " +
            "FROM trip WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<TripSummary> listSummaries(@Param("userId") Long userId);

    @Select("SELECT id, user_id, city, preferences, budget, days, energy_level, extra, status, created_at, updated_at " +
            "FROM trip WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit}")
    List<Trip> findRecentByUser(@Param("userId") Long userId, @Param("limit") int limit);

    @Delete("DELETE FROM trip WHERE id = #{id} AND user_id = #{userId}")
    int deleteByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);
}
