package com.study.travel_guide.mapper;

import com.study.travel_guide.dto.TripSummary;
import com.study.travel_guide.entity.Trip;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface TripMapper {

    @Insert("INSERT INTO trip(user_id, city, start_date, preferences, budget, days, people_count, energy_level, transportation, extra_requirements, status, result) " +
            "VALUES(#{userId}, #{city}, #{startDate}, #{preferences}, #{budget}, #{days}, #{peopleCount}, #{energyLevel}, #{transportation}, #{extraRequirements}, #{status}, #{result})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Trip trip);

    @Select("SELECT * FROM trip WHERE id = #{id} AND user_id = #{userId}")
    Trip findByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    @Select("<script>SELECT id, CONCAT(city, ' ', days, '天') AS title, is_favorite, created_at " +
            "FROM trip WHERE user_id = #{userId} " +
            "<if test='favorite != null'>AND is_favorite = #{favorite}</if> " +
            "ORDER BY created_at DESC</script>")
    List<TripSummary> listSummaries(@Param("userId") Long userId, @Param("favorite") Boolean favorite);

    @Select("SELECT id, user_id, city, start_date, preferences, budget, days, people_count, energy_level, transportation, extra_requirements, status, created_at, updated_at " +
            "FROM trip WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit}")
    List<Trip> findRecentByUser(@Param("userId") Long userId, @Param("limit") int limit);

    @Delete("DELETE FROM trip WHERE id = #{id} AND user_id = #{userId}")
    int deleteByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    @Update("UPDATE trip SET share_token = #{token} WHERE id = #{id} AND user_id = #{userId}")
    int updateShareToken(@Param("id") Long id, @Param("userId") Long userId, @Param("token") String token);

    @Update("UPDATE trip SET share_token = NULL WHERE id = #{id} AND user_id = #{userId}")
    int clearShareToken(@Param("id") Long id, @Param("userId") Long userId);

    @Update("UPDATE trip SET result = #{result} WHERE id = #{id} AND user_id = #{userId}")
    int updateResult(@Param("id") Long id, @Param("userId") Long userId, @Param("result") String result);

    @Update("UPDATE trip SET is_favorite = #{favorite} WHERE id = #{id} AND user_id = #{userId}")
    int updateFavorite(@Param("id") Long id, @Param("userId") Long userId, @Param("favorite") boolean favorite);

    @Select("SELECT * FROM trip WHERE share_token = #{token}")
    Trip findByShareToken(@Param("token") String token);

    @Select("SELECT COUNT(*) FROM trip WHERE user_id = #{userId}")
    int countByUser(@Param("userId") Long userId);

    @Select("SELECT COUNT(DISTINCT city) FROM trip WHERE user_id = #{userId}")
    int countDistinctCity(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM trip WHERE user_id = #{userId} AND is_favorite = 1")
    int countFavorite(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM trip")
    int countAll();

    @Select("SELECT COUNT(DISTINCT user_id) FROM trip WHERE DATE(created_at) = CURDATE()")
    int countDistinctUserToday();

    @Select("SELECT city, COUNT(*) AS cnt FROM trip GROUP BY city ORDER BY cnt DESC LIMIT 5")
    List<Map<String, Object>> topCities();
}
