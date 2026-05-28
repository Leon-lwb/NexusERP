package com.nexus.erp.prd.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.PrdReworkOrder;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdReworkOrderBo;
import com.nexus.erp.prd.domain.vo.PrdReworkOrderVo;
import com.nexus.erp.prd.mapper.PrdReworkOrderMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.service.IPrdReworkOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 返工订单Service业务层处理 * 状态流转：DRAFT -> IN_PROGRESS -> COMPLETED
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PrdReworkOrderServiceImpl implements IPrdReworkOrderService {

    private final PrdReworkOrderMapper baseMapper;
    private final PrdWorkOrderMapper workOrderMapper;

    @Override
    public TableDataInfo<PrdReworkOrderVo> selectPageList(PrdReworkOrderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PrdReworkOrder> lqw = buildQueryWrapper(bo);
        Page<PrdReworkOrderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<PrdReworkOrderVo> selectList(PrdReworkOrderBo bo) {
        LambdaQueryWrapper<PrdReworkOrder> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    @Override
    public PrdReworkOrderVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PrdReworkOrderBo bo) {
        if (bo.getSourceWoId() != null) {
            PrdWorkOrder sourceWo = workOrderMapper.selectById(bo.getSourceWoId());
            if (sourceWo == null) {
                throw new ServiceException("来源工单不存在");
            }
            bo.setSourceWoNo(sourceWo.getWoNo());
            bo.setMaterialId(sourceWo.getMaterialId());
            bo.setMaterialCode(sourceWo.getMaterialCode());
            bo.setMaterialName(sourceWo.getMaterialName());
            bo.setMaterialSpec(sourceWo.getMaterialSpec());
            bo.setBaseUomName(sourceWo.getUnitName());
        }
        PrdReworkOrder rework = MapstructUtils.convert(bo, PrdReworkOrder.class);
        return baseMapper.insert(rework);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PrdReworkOrderBo bo) {
        PrdReworkOrder rework = MapstructUtils.convert(bo, PrdReworkOrder.class);
        return baseMapper.updateById(rework);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void start(Long reworkId) {
        PrdReworkOrder rework = baseMapper.selectById(reworkId);
        if (rework == null) {
            throw new ServiceException("返工订单不存在");
        }
        if (!STATUS_DRAFT.equals(rework.getStatus())) {
            throw new ServiceException("仅草稿状态可开始，当前状态：" + rework.getStatus());
        }
        rework.setStatus(STATUS_IN_PROGRESS);
        baseMapper.updateById(rework);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long reworkId) {
        PrdReworkOrder rework = baseMapper.selectById(reworkId);
        if (rework == null) {
            throw new ServiceException("返工订单不存在");
        }
        if (!STATUS_IN_PROGRESS.equals(rework.getStatus())) {
            throw new ServiceException("仅进行中状态可完工，当前状态：" + rework.getStatus());
        }
        rework.setStatus(STATUS_COMPLETED);
        baseMapper.updateById(rework);

        if (rework.getSourceWoId() != null && rework.getScrapQty() != null
            && rework.getScrapQty().compareTo(BigDecimal.ZERO) > 0) {
            PrdWorkOrder sourceWo = workOrderMapper.selectById(rework.getSourceWoId());
            if (sourceWo != null) {
                BigDecimal currentScrap = sourceWo.getScrapQty() != null ? sourceWo.getScrapQty() : BigDecimal.ZERO;
                sourceWo.setScrapQty(currentScrap.add(rework.getScrapQty()));
                workOrderMapper.updateById(sourceWo);
            }
        }
    }

    private LambdaQueryWrapper<PrdReworkOrder> buildQueryWrapper(PrdReworkOrderBo bo) {
        LambdaQueryWrapper<PrdReworkOrder> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getReworkNo()), PrdReworkOrder::getReworkNo, bo.getReworkNo());
        lqw.like(StringUtils.isNotBlank(bo.getSourceWoNo()), PrdReworkOrder::getSourceWoNo, bo.getSourceWoNo());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialCode()), PrdReworkOrder::getMaterialCode, bo.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), PrdReworkOrder::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), PrdReworkOrder::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getReworkReason()), PrdReworkOrder::getReworkReason, bo.getReworkReason());
        lqw.orderByDesc(PrdReworkOrder::getCreateTime);
        return lqw;
    }
}
