package com.nexus.common.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.ai.domain.bo.AiKgRelationBo;
import com.nexus.common.ai.domain.entity.AiKgRelation;
import com.nexus.common.ai.domain.vo.AiKgEntityVo;
import com.nexus.common.ai.domain.vo.AiKgRelationVo;
import com.nexus.common.ai.mapper.AiKgEntityMapper;
import com.nexus.common.ai.mapper.AiKgRelationMapper;
import com.nexus.common.ai.service.IAiKgRelationService;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 知识图谱关系 Service 实现
 * <p>关系列表回填 sourceEntityName/targetEntityName</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@RequiredArgsConstructor
@Service
public class AiKgRelationServiceImpl implements IAiKgRelationService {

    private final AiKgRelationMapper baseMapper;
    private final AiKgEntityMapper entityMapper;

    @Override
    public TableDataInfo<AiKgRelationVo> selectPageList(AiKgRelationBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<AiKgRelation> lqw = buildQueryWrapper(bo);
        Page<AiKgRelationVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        fillEntityNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<AiKgRelationVo> selectByGraphId(Long graphId) {
        List<AiKgRelationVo> list = baseMapper.selectVoList(Wrappers.<AiKgRelation>lambdaQuery()
            .eq(AiKgRelation::getGraphId, graphId)
            .orderByDesc(AiKgRelation::getStrength));
        fillEntityNames(list);
        return list;
    }

    private LambdaQueryWrapper<AiKgRelation> buildQueryWrapper(AiKgRelationBo bo) {
        return Wrappers.<AiKgRelation>lambdaQuery()
            .eq(bo.getGraphId() != null, AiKgRelation::getGraphId, bo.getGraphId())
            .eq(bo.getRelationType() != null, AiKgRelation::getRelationType, bo.getRelationType())
            .eq(bo.getSourceEntityId() != null, AiKgRelation::getSourceEntityId, bo.getSourceEntityId())
            .orderByDesc(AiKgRelation::getStrength);
    }

    private void fillEntityNames(List<AiKgRelationVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> ids = new HashSet<>();
        for (AiKgRelationVo r : records) {
            if (r.getSourceEntityId() != null) {
                ids.add(r.getSourceEntityId());
            }
            if (r.getTargetEntityId() != null) {
                ids.add(r.getTargetEntityId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = entityMapper.selectVoByIds(ids).stream()
            .collect(Collectors.toMap(AiKgEntityVo::getId, AiKgEntityVo::getEntityName, (a, b) -> a));
        for (AiKgRelationVo r : records) {
            r.setSourceEntityName(nameMap.get(r.getSourceEntityId()));
            r.setTargetEntityName(nameMap.get(r.getTargetEntityId()));
        }
    }
}
