package com.nexus.im.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexus.im.domain.ImFriend;

import java.util.List;

/**
 * IM好友服务接口
 *
 * @author nexus
 */
public interface ImFriendService extends IService<ImFriend> {
    
    List<ImFriend> getFriendList(Long userId);
    
    boolean addFriend(Long userId, Long friendUserId, String remark);
    
    boolean removeFriend(Long userId, Long friendUserId);
    
    boolean isFriend(Long userId, Long friendUserId);
    
    boolean updateFriendRemark(Long userId, Long friendUserId, String remark);
}
