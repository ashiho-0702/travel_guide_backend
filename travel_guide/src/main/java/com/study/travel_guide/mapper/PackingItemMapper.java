package com.study.travel_guide.mapper;

import com.study.travel_guide.entity.PackingItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PackingItemMapper {

    @Insert("INSERT INTO packing_item(trip_id, name, checked) VALUES(#{tripId}, #{name}, #{checked})")
    int insert(@Param("tripId") Long tripId, @Param("name") String name, @Param("checked") boolean checked);

    @Select("SELECT * FROM packing_item WHERE trip_id = #{tripId} ORDER BY id ASC")
    List<PackingItem> listByTrip(@Param("tripId") Long tripId);

    @Update("UPDATE packing_item SET name = #{name}, checked = #{checked} WHERE id = #{id} AND trip_id = #{tripId}")
    int update(@Param("id") Long id, @Param("tripId") Long tripId, @Param("name") String name, @Param("checked") boolean checked);

    @Delete("DELETE FROM packing_item WHERE id = #{id} AND trip_id = #{tripId}")
    int delete(@Param("id") Long id, @Param("tripId") Long tripId);

    @Delete("DELETE FROM packing_item WHERE trip_id = #{tripId}")
    int deleteByTrip(@Param("tripId") Long tripId);
}
