package com.nexus.common.ai.service;

import com.nexus.common.ai.domain.entity.AiSession;

import java.util.List;

/**
 * AI 对话会话 Service 接口
 *
 * @author NexusERP
 */
public interface IAiSessionService {

    /**
     * 根据用户ID查询会话列表
     *
     * @param userId 用户ID
     * @return 会话列表（按创建时间倒序）
     */
    List<AiSession> selectByUserId(Long userId);

    /**
     * 根据ID查询会话
     *
     * @param id 会话ID
     * @return 会话信息
     */
    AiSession selectById(Long id);

    /**
     * 新增会话
     *
     * @param session 会话信息
     * @return 影响行数
     */
    int insert(AiSession session);

    /**
     * 更新会话标题
     *
     * @param id    会话ID
     * @param title 新标题
     * @return 影响行数
     */
    int updateTitle(Long id, String title);

    /**
     * 批量删除会话（级联删除消息）
     *
     * @param ids 会话ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

}
