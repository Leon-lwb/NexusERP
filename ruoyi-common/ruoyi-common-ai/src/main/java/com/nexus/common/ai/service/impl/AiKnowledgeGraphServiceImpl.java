package com.nexus.common.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.ai.domain.bo.AiKnowledgeGraphBo;
import com.nexus.common.ai.domain.entity.AiKnowledgeGraph;
import com.nexus.common.ai.domain.vo.AiKnowledgeGraphVo;
import com.nexus.common.ai.mapper.AiKnowledgeGraphMapper;
import com.nexus.common.ai.service.IAiKnowledgeGraphService;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 知识图谱实例 Service 实现
 *
 * @author NexusERP
 * @since 2.0.0
 */
@RequiredArgsConstructor
@Service
public class AiKnowledgeGraphServiceImpl implements IAiKnowledgeGraphService {

    private final AiKnowledgeGraphMapper baseMapper;

    @Override
    public AiKnowledgeGraphVo selectByKnowledgeId(Long knowledgeId) {
        List<AiKnowledgeGraphVo> list = baseMapper.selectVoList(Wrappers.<AiKnowledgeGraph>lambdaQuery()
            .eq(AiKnowledgeGraph::getKnowledgeId, knowledgeId)
            .orderByDesc(AiKnowledgeGraph::getCreateTime));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public TableDataInfo<AiKnowledgeGraphVo> selectPageList(AiKnowledgeGraphBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<AiKnowledgeGraph> lqw = buildQueryWrapper(bo);
        Page<AiKnowledgeGraphVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    private LambdaQueryWrapper<AiKnowledgeGraph> buildQueryWrapper(AiKnowledgeGraphBo bo) {
        return Wrappers.<AiKnowledgeGraph>lambdaQuery()
            .eq(bo.getKnowledgeId() != null, AiKnowledgeGraph::getKnowledgeId, bo.getKnowledgeId())
            .eq(bo.getGraphStatus() != null, AiKnowledgeGraph::getGraphStatus, bo.getGraphStatus())
            .like(bo.getGraphName() != null, AiKnowledgeGraph::getGraphName, bo.getGraphName())
            .orderByDesc(AiKnowledgeGraph::getCreateTime);
    }
}
