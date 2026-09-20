package com.study.travel_guide.service.growth;

import com.study.travel_guide.mapper.InviteRelationMapper;
import com.study.travel_guide.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class InviteService {

    private final InviteRelationMapper inviteRelationMapper;
    private final PointService pointService;
    private final UserMapper userMapper;

    public InviteService(InviteRelationMapper inviteRelationMapper, PointService pointService, UserMapper userMapper) {
        this.inviteRelationMapper = inviteRelationMapper;
        this.pointService = pointService;
        this.userMapper = userMapper;
    }

    @Transactional
    public void bindInvite(Long inviterId, Long inviteeId) {
        if (inviterId == null || inviteeId == null || inviterId.equals(inviteeId)) {
            return;
        }
        if (userMapper.findById(inviterId) == null) {
            return;
        }
        if (inviteRelationMapper.findByInvitee(inviteeId) != null) {
            return;
        }
        inviteRelationMapper.insert(inviterId, inviteeId);
        pointService.addPoints(inviterId, "invite", GrowthRule.INVITE, "邀请好友");
        pointService.addPoints(inviteeId, "invite_reward", GrowthRule.INVITE_REWARD, "被邀请奖励");
    }

    public Map<String, Object> inviteInfo(Long userId) {
        int count = inviteRelationMapper.countByInviter(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("invitedCount", count);
        data.put("invitePoints", count * GrowthRule.INVITE);
        return data;
    }
}
