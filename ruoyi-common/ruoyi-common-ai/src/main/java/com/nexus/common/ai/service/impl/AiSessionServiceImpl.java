package com.nexus.common.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.ai.domain.entity.AiMessage;
import com.nexus.common.ai.domain.entity.AiSession;
import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.ai.mapper.AiSessionMapper;
import com.nexus.common.ai.service.IAiSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 对话会话 Service 业务层处理
 *
 * @author NexusERP
 */
@RequiredArgsConstructor
@Service
public class AiSessionServiceImpl implements IAiSessionService {

    private final AiSessionMapper aiSessionMapper;
    private final AiMessageMapper aiMessageMapper;

    /**
     * 根据用户ID查询会话列表
     *
     * @param userId 用户ID
     * @return 会话列表（按创建时间倒序）
     */
    @Override
    public List<AiSession> selectByUserId(Long userId) {
        return aiSessionMapper.selectList(
            new LambdaQueryWrapper<AiSession>()
                .eq(AiSession::getUserId, userId)
                .orderByDesc(AiSession::getCreateTime)
        );
    }

    /**
     * 根据ID查询会话
     *
     * @param id 会话ID
     * @return 会话信息
     */
    @Override
    public AiSession selectById(Long id) {
        return aiSessionMapper.selectById(id);
    }

    /**
     * 新增会话
     *
     * @param session 会话信息
     * @return 影响行数
     */
    @Override
    public int insert(AiSession session) {
        return aiSessionMapper.insert(session);
    }

    /**
     * 更新会话标题
     *
     * @param id    会话ID
     * @param title 新标题
     * @return 影响行数
     */
    @Override
    public int updateTitle(Long id, String title) {
        AiSession session = new AiSession();
        session.setId(id);
        session.setTitle(title);
        return aiSessionMapper.updateById(session);
    }

    /**
     * 批量删除会话（级联删除消息）
     *
     * @param ids 会话ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        // 1. 级联删除会话下的所有消息
        for (Long id : ids) {
            aiMessageMapper.delete(
                new LambdaQueryWrapper<AiMessage>()
                    .eq(AiMessage::getSessionId, id)
            );
        }
        // 2. 删除会话
        return aiSessionMapper.deleteBatchIds(List.of(ids));
    }

}
