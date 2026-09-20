package com.study.travel_guide.mapper;

import com.study.travel_guide.entity.InviteRelation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InviteRelationMapper {

    @Insert("INSERT INTO invite_relation(inviter_id, invitee_id) VALUES(#{inviterId}, #{inviteeId})")
    int insert(@Param("inviterId") Long inviterId, @Param("inviteeId") Long inviteeId);

    @Select("SELECT * FROM invite_relation WHERE invitee_id = #{inviteeId}")
    InviteRelation findByInvitee(@Param("inviteeId") Long inviteeId);

    @Select("SELECT COUNT(*) FROM invite_relation WHERE inviter_id = #{inviterId}")
    int countByInviter(@Param("inviterId") Long inviterId);
}
