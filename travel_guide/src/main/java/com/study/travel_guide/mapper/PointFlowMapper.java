package com.study.travel_guide.mapper;

import com.study.travel_guide.entity.PointFlow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PointFlowMapper {

    @Insert("INSERT INTO point_flow(user_id, points, type, remark) VALUES(#{userId}, #{points}, #{type}, #{remark})")
    int insert(@Param("userId") Long userId, @Param("points") int points, @Param("type") String type, @Param("remark") String remark);

    @Select("SELECT * FROM point_flow WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit}")
    List<PointFlow> listByUser(@Param("userId") Long userId, @Param("limit") int limit);
}
