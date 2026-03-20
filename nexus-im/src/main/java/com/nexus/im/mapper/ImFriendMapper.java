package com.nexus.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.im.domain.ImFriend;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * IM好友Mapper接口
 *
 * @author nexus
 */
@Mapper
public interface ImFriendMapper extends BaseMapper<ImFriend> {
    
    List<ImFriend> selectFriendList(@Param("userId") Long userId);
    
    ImFriend selectFriendRelation(@Param("userId") Long userId, 
                                 @Param("friendUserId") Long friendUserId);
    
    Integer selectFriendCount(@Param("userId") Long userId);
}
