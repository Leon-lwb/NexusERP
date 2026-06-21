package com.nexus.erp.bd.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdRouting;
import com.nexus.erp.bd.domain.BdRoutingStep;
import com.nexus.erp.bd.domain.BdBom;
import com.nexus.erp.bd.domain.bo.BdRoutingBo;
import com.nexus.erp.bd.domain.bo.BdRoutingStepBo;
import com.nexus.erp.bd.domain.vo.BdRoutingStepVo;
import com.nexus.erp.bd.domain.vo.BdRoutingVo;
import com.nexus.erp.bd.mapper.BdBomMapper;
import com.nexus.erp.bd.mapper.BdRoutingMapper;
import com.nexus.erp.bd.mapper.BdRoutingStepMapper;
import com.nexus.erp.bd.service.IBdRoutingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 工艺路线Service业务层处理
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdRoutingServiceImpl implements IBdRoutingService {

    private final BdRoutingMapper baseMapper;
    private final BdRoutingStepMapper stepMapper;
    private final BdBomMapper bomMapper;

    @Override
    public TableDataInfo<BdRoutingVo> selectPageList(BdRoutingBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdRouting> lqw = buildQueryWrapper(bo);
        Page<BdRoutingVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<BdRoutingVo> selectList(BdRoutingBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public BdRoutingVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdRoutingBo bo) {
        BdRouting entity = MapstructUtils.convert(bo, BdRouting.class);
        computeTotals(entity, bo.getSteps());
        int rows = baseMapper.insert(entity);
        saveSteps(entity.getRoutingId(), bo.getSteps());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdRoutingBo bo) {
        BdRouting entity = MapstructUtils.convert(bo, BdRouting.class);
        computeTotals(entity, bo.getSteps());
        int rows = baseMapper.updateById(entity);
        saveSteps(entity.getRoutingId(), bo.getSteps());
        return rows;
    }

    /**
     * 保存工艺路线步骤(先删后插)
     */
    private void saveSteps(Long routingId, List<BdRoutingStepBo> steps) {
        if (routingId == null) {
            return;
        }
        stepMapper.delete(Wrappers.lambdaQuery(BdRoutingStep.class)
            .eq(BdRoutingStep::getRoutingId, routingId));
        if (steps == null || steps.isEmpty()) {
            return;
        }
        for (BdRoutingStepBo stepBo : steps) {
            BdRoutingStep step = MapstructUtils.convert(stepBo, BdRoutingStep.class);
            step.setStepId(null);
            step.setRoutingId(routingId);
            stepMapper.insert(step);
        }
    }

    /**
     * 根据步骤列表计算总工序数与总工时
     */
    private void computeTotals(BdRouting entity, List<BdRoutingStepBo> steps) {
        if (steps == null || steps.isEmpty()) {
            entity.setTotalSteps(0);
            entity.setTotalHours(BigDecimal.ZERO);
            return;
        }
        entity.setTotalSteps(steps.size());
        BigDecimal totalHours = BigDecimal.ZERO;
        for (BdRoutingStepBo s : steps) {
            if (s.getStandardHours() != null) {
                totalHours = totalHours.add(s.getStandardHours());
            }
            if (s.getSetupHours() != null) {
                totalHours = totalHours.add(s.getSetupHours());
            }
        }
        entity.setTotalHours(totalHours);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        // 校验是否有BOM引用这些工艺路线
        Long ref = bomMapper.selectCount(
            Wrappers.lambdaQuery(BdBom.class)
                .in(BdBom::getRoutingId, List.of(ids)));
        if (ref != null && ref > 0) {
            throw new ServiceException("所选工艺路线已被" + ref + "个BOM引用，无法删除");
        }
        // 级联删除步骤
        for (Long routingId : ids) {
            stepMapper.delete(Wrappers.lambdaQuery(BdRoutingStep.class)
                .eq(BdRoutingStep::getRoutingId, routingId));
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateVersion(Long routingId) {
        BdRouting routing = baseMapper.selectById(routingId);
        if (routing == null) {
            throw new ServiceException("工艺路线不存在");
        }
        if (routing.getMaterialId() == null) {
            throw new ServiceException("工艺路线未关联物料，无法激活");
        }
        // 将该物料所有工艺路线版本设为非当前
        List<BdRouting> sameMaterialRoutings = baseMapper.selectList(
            Wrappers.lambdaQuery(BdRouting.class)
                .eq(BdRouting::getMaterialId, routing.getMaterialId()));
        for (BdRouting r : sameMaterialRoutings) {
            r.setIsCurrent(0L);
            baseMapper.updateById(r);
        }
        // 激活当前版本
        routing.setIsCurrent(1L);
        routing.setStatus(1L);
        baseMapper.updateById(routing);
    }

    @Override
    public BdRoutingVo getCurrentRouting(Long materialId) {
        if (materialId == null) {
            return null;
        }
        BdRouting routing = baseMapper.selectOne(
            Wrappers.lambdaQuery(BdRouting.class)
                .eq(BdRouting::getMaterialId, materialId)
                .eq(BdRouting::getIsCurrent, 1L)
                .eq(BdRouting::getStatus, 1L));
        if (routing == null) {
            // 如果没有物料专属的工艺路线，尝试获取通用工艺路线
            routing = baseMapper.selectOne(
                Wrappers.lambdaQuery(BdRouting.class)
                    .isNull(BdRouting::getMaterialId)
                    .eq(BdRouting::getIsCurrent, 1L)
                    .eq(BdRouting::getStatus, 1L));
        }
        return routing != null ? MapstructUtils.convert(routing, BdRoutingVo.class) : null;
    }

    @Override
    public List<BdRoutingStepVo> getRoutingSteps(Long routingId) {
        return stepMapper.selectVoList(
            Wrappers.lambdaQuery(BdRoutingStep.class)
                .eq(BdRoutingStep::getRoutingId, routingId)
                .orderByAsc(BdRoutingStep::getSequenceNum));
    }

    @Override
    public String verifyRouting(Long routingId) {
        BdRouting routing = baseMapper.selectById(routingId);
        if (routing == null) {
            return "工艺路线不存在";
        }
        // 检查是否有步骤
        List<BdRoutingStep> steps = stepMapper.selectList(
            Wrappers.lambdaQuery(BdRoutingStep.class)
                .eq(BdRoutingStep::getRoutingId, routingId));
        if (steps == null || steps.isEmpty()) {
            return "工艺路线步骤为空，请添加工序步骤";
        }
        // 检查步骤序号连续性
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getSequenceNum() == null || steps.get(i).getSequenceNum() != i + 1) {
                return "工艺路线步骤序号不连续，期望序号 " + (i + 1) + "，实际 " + steps.get(i).getSequenceNum();
            }
            if (steps.get(i).getProcessId() == null) {
                return "第 " + (i + 1) + " 步工序为空";
            }
        }
        return "OK";
    }

    private LambdaQueryWrapper<BdRouting> buildQueryWrapper(BdRoutingBo bo) {
        LambdaQueryWrapper<BdRouting> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getRoutingCode()), BdRouting::getRoutingCode, bo.getRoutingCode());
        lqw.like(StringUtils.isNotBlank(bo.getRoutingName()), BdRouting::getRoutingName, bo.getRoutingName());
        lqw.eq(bo.getMaterialId() != null, BdRouting::getMaterialId, bo.getMaterialId());
        lqw.eq(bo.getIsCurrent() != null, BdRouting::getIsCurrent, bo.getIsCurrent());
        lqw.eq(bo.getStatus() != null, BdRouting::getStatus, bo.getStatus());
        return lqw;
    }
}
