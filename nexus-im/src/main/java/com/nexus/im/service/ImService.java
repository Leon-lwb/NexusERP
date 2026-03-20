package com.nexus.im.service;

import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.core.domain.entity.SysUser;

import java.util.List;
import java.util.Map;

/**
 * IM服务接口
 *
 * @author nexus
 */
public interface ImService {

    AjaxResult loginToIm(SysUser user);

    AjaxResult logoutFromIm(Long userId);

    AjaxResult syncUserToIm(SysUser user);

    AjaxResult batchSyncUsersToIm(List<SysUser> users);

    AjaxResult sendMessage(Long fromUserId, Long toUserId, String content, String messageType);

    AjaxResult sendGroupMessage(Long fromUserId, String groupId, String content, String messageType);

    AjaxResult getUserImInfo(Long userId);

    AjaxResult getImUserList();

    AjaxResult getMessageHistory(Long userId, String targetId, Integer pageNum, Integer pageSize);
    
    String getMessageId(Long fromUserId, Long toUserId, String content, String messageType);

    AjaxResult createGroup(Long ownerId, String groupName, String description, String avatar, List<Long> memberIds);

    AjaxResult getUserGroups(Long userId);

    AjaxResult getConversationList(Long userId);

    AjaxResult markMessagesAsRead(Long userId, Long toUserId, String groupId);

    AjaxResult getUnreadMessages(Long userId);

    AjaxResult getGroupInfo(String groupId);

    AjaxResult getGroupMembers(String groupId);

    AjaxResult addGroupMembers(String groupId, Long operatorId, List<Long> memberIds);

    AjaxResult removeGroupMember(String groupId, Long operatorId, Long userId);

    AjaxResult updateGroupInfo(String groupId, Long operatorId, String groupName, String description, String avatar);

    AjaxResult quitGroup(String groupId, Long userId);

    AjaxResult disbandGroup(String groupId, Long operatorId);

    AjaxResult getUnreadCount(Long userId);
}
