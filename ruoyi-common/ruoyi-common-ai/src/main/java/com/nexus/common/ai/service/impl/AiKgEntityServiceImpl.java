package com.nexus.common.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.ai.domain.bo.AiKgEntityBo;
import com.nexus.common.ai.domain.entity.AiKgEntity;
import com.nexus.common.ai.domain.vo.AiKgEntityVo;
import com.nexus.common.ai.mapper.AiKgEntityMapper;
import com.nexus.common.ai.service.IAiKgEntityService;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 知识图谱实体 Service 实现
 *
 * @author NexusERP
 * @since 2.0.0
 */
@RequiredArgsConstructor
@Service
public class AiKgEntityServiceImpl implements IAiKgEntityService {

    private final AiKgEntityMapper baseMapper;

    @Override
    public TableDataInfo<AiKgEntityVo> selectPageList(AiKgEntityBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<AiKgEntity> lqw = buildQueryWrapper(bo);
        Page<AiKgEntityVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<AiKgEntityVo> selectByGraphId(Long graphId) {
        return baseMapper.selectVoList(Wrappers.<AiKgEntity>lambdaQuery()
            .eq(AiKgEntity::getGraphId, graphId)
            .orderByDesc(AiKgEntity::getMentionCount));
    }

    private LambdaQueryWrapper<AiKgEntity> buildQueryWrapper(AiKgEntityBo bo) {
        return Wrappers.<AiKgEntity>lambdaQuery()
            .eq(bo.getGraphId() != null, AiKgEntity::getGraphId, bo.getGraphId())
            .eq(bo.getEntityType() != null, AiKgEntity::getEntityType, bo.getEntityType())
            .eq(bo.getCommunityId() != null, AiKgEntity::getCommunityId, bo.getCommunityId())
            .like(bo.getEntityName() != null, AiKgEntity::getEntityName, bo.getEntityName())
            .orderByDesc(AiKgEntity::getMentionCount);
    }
}
