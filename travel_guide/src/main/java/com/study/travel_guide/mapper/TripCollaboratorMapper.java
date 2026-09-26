package com.study.travel_guide.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface TripCollaboratorMapper {

    @Insert("INSERT INTO trip_collaborator(trip_id, user_id) VALUES(#{tripId}, #{userId})")
    int insert(@Param("tripId") Long tripId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM trip_collaborator WHERE trip_id = #{tripId} AND user_id = #{userId}")
    int exists(@Param("tripId") Long tripId, @Param("userId") Long userId);

    @Select("SELECT c.user_id AS userId, u.nickname AS nickname, u.avatar_url AS avatarUrl " +
            "FROM trip_collaborator c JOIN `user` u ON u.id = c.user_id " +
            "WHERE c.trip_id = #{tripId} ORDER BY c.joined_at ASC")
    List<Map<String, Object>> listByTrip(@Param("tripId") Long tripId);

    @Delete("DELETE FROM trip_collaborator WHERE trip_id = #{tripId} AND user_id = #{userId}")
    int delete(@Param("tripId") Long tripId, @Param("userId") Long userId);
}
