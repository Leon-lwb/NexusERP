package com.nexus.im.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.im.core.ImMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * IM消息Mapper接口
 *
 * @author nexus
 */
@Mapper
public interface ImMessageMapper extends BaseMapper<ImMessage> {
    
    List<ImMessage> selectPrivateMessageHistory(@Param("userId1") Long userId1, 
                                               @Param("userId2") Long userId2,
                                               @Param("offset") Integer offset, 
                                               @Param("limit") Integer limit);
    
    List<ImMessage> selectGroupMessageHistory(@Param("groupId") String groupId,
                                             @Param("offset") Integer offset, 
                                             @Param("limit") Integer limit);
    
    Integer selectUnreadCount(@Param("userId") Long userId);
    
    Integer selectUnreadCountByUser(@Param("userId") Long userId, 
                                   @Param("targetUserId") Long targetUserId);
    
    Integer selectGroupUnreadCount(@Param("userId") Long userId, 
                                  @Param("groupId") String groupId,
                                  @Param("joinTime") String joinTime);
}
