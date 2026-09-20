package com.study.travel_guide.mapper;

import com.study.travel_guide.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM `user` WHERE openid = #{openid}")
    User findByOpenid(String openid);

    @Select("SELECT * FROM `user` WHERE id = #{id}")
    User findById(Long id);

    @Insert("INSERT INTO `user`(openid, nickname, avatar_url) VALUES(#{openid}, #{nickname}, #{avatarUrl})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("UPDATE `user` SET nickname = #{nickname} WHERE id = #{id}")
    int updateNickname(@Param("id") Long id, @Param("nickname") String nickname);

    @Update("UPDATE `user` SET avatar_url = #{avatarUrl} WHERE id = #{id}")
    int updateAvatar(@Param("id") Long id, @Param("avatarUrl") String avatarUrl);

    @Update("UPDATE `user` SET points = points + #{pointsDelta}, growth = growth + #{growthDelta}, " +
            "level = CASE WHEN growth + #{growthDelta} >= 1000 THEN 5 WHEN growth + #{growthDelta} >= 600 THEN 4 " +
            "WHEN growth + #{growthDelta} >= 300 THEN 3 WHEN growth + #{growthDelta} >= 100 THEN 2 ELSE 1 END " +
            "WHERE id = #{id}")
    int addGrowth(@Param("id") Long id, @Param("pointsDelta") int pointsDelta, @Param("growthDelta") int growthDelta);
}
