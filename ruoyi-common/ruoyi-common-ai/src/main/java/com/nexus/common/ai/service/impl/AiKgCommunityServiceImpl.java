package com.nexus.common.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.ai.domain.bo.AiKgCommunityBo;
import com.nexus.common.ai.domain.entity.AiKgCommunity;
import com.nexus.common.ai.domain.vo.AiKgCommunityVo;
import com.nexus.common.ai.mapper.AiKgCommunityMapper;
import com.nexus.common.ai.service.IAiKgCommunityService;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 知识图谱社区 Service 实现
 *
 * @author NexusERP
 * @since 2.0.0
 */
@RequiredArgsConstructor
@Service
public class AiKgCommunityServiceImpl implements IAiKgCommunityService {

    private final AiKgCommunityMapper baseMapper;

    @Override
    public TableDataInfo<AiKgCommunityVo> selectPageList(AiKgCommunityBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<AiKgCommunity> lqw = buildQueryWrapper(bo);
        Page<AiKgCommunityVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<AiKgCommunityVo> selectByGraphId(Long graphId) {
        return baseMapper.selectVoList(Wrappers.<AiKgCommunity>lambdaQuery()
            .eq(AiKgCommunity::getGraphId, graphId)
            .orderByAsc(AiKgCommunity::getCommunityIndex));
    }

    private LambdaQueryWrapper<AiKgCommunity> buildQueryWrapper(AiKgCommunityBo bo) {
        return Wrappers.<AiKgCommunity>lambdaQuery()
            .eq(bo.getGraphId() != null, AiKgCommunity::getGraphId, bo.getGraphId())
            .eq(bo.getSummaryStatus() != null, AiKgCommunity::getSummaryStatus, bo.getSummaryStatus())
            .orderByAsc(AiKgCommunity::getCommunityIndex);
    }
}
