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
import com.nexus.erp.prd.domain.PrdSubcontract;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdSubcontractBo;
import com.nexus.erp.prd.domain.vo.PrdSubcontractVo;
import com.nexus.erp.prd.mapper.PrdSubcontractMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.service.IPrdSubcontractService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 委外订单Service业务层处理 * 状态流转：DRAFT -> SUBMITTED -> APPROVED -> IN_PROGRESS -> COMPLETED -> CLOSED
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PrdSubcontractServiceImpl implements IPrdSubcontractService {

    private final PrdSubcontractMapper baseMapper;
    private final PrdWorkOrderMapper workOrderMapper;

    @Override
    public TableDataInfo<PrdSubcontractVo> selectPageList(PrdSubcontractBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PrdSubcontract> lqw = buildQueryWrapper(bo);
        Page<PrdSubcontractVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<PrdSubcontractVo> selectList(PrdSubcontractBo bo) {
        LambdaQueryWrapper<PrdSubcontract> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    @Override
    public PrdSubcontractVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PrdSubcontractBo bo) {
        PrdSubcontract subcontract = MapstructUtils.convert(bo, PrdSubcontract.class);
        if (subcontract.getPlannedQty() != null && subcontract.getUnitPrice() != null) {
            subcontract.setTotalAmount(subcontract.getPlannedQty().multiply(subcontract.getUnitPrice()));
        }
        return baseMapper.insert(subcontract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PrdSubcontractBo bo) {
        PrdSubcontract subcontract = MapstructUtils.convert(bo, PrdSubcontract.class);
        if (subcontract.getPlannedQty() != null && subcontract.getUnitPrice() != null) {
            subcontract.setTotalAmount(subcontract.getPlannedQty().multiply(subcontract.getUnitPrice()));
        }
        return baseMapper.updateById(subcontract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long subcontractId) {
        PrdSubcontract subcontract = baseMapper.selectById(subcontractId);
        if (subcontract == null) {
            throw new ServiceException("委外订单不存在");
        }
        if (!STATUS_DRAFT.equals(subcontract.getStatus()) && !STATUS_APPROVED.equals(subcontract.getStatus())) {
            throw new ServiceException("仅草稿或已批准状态可下达，当前状态：" + subcontract.getStatus());
        }
        subcontract.setStatus(STATUS_RELEASED);
        baseMapper.updateById(subcontract);

        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoNo("WO-SC-" + subcontract.getSubcontractNo());
        wo.setMaterialId(subcontract.getMaterialId());
        wo.setMaterialCode(subcontract.getMaterialCode());
        wo.setMaterialName(subcontract.getMaterialName());
        wo.setMaterialSpec(subcontract.getMaterialSpec());
        wo.setPlannedQty(subcontract.getPlannedQty());
        wo.setUnitName(subcontract.getBaseUomName());
        wo.setPlanStartDate(subcontract.getPlanStartDate());
        wo.setPlanEndDate(subcontract.getPlanEndDate());
        wo.setWoStatus("RELEASED");
        wo.setSourceType("SUBCONTRACT");
        wo.setRemark("委外订单自动生成");
        workOrderMapper.insert(wo);

        subcontract.setWoId(wo.getWoId());
        subcontract.setWoNo(wo.getWoNo());
        baseMapper.updateById(subcontract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void start(Long subcontractId) {
        PrdSubcontract subcontract = baseMapper.selectById(subcontractId);
        if (subcontract == null) {
            throw new ServiceException("委外订单不存在");
        }
        if (!STATUS_RELEASED.equals(subcontract.getStatus()) && !STATUS_APPROVED.equals(subcontract.getStatus())) {
            throw new ServiceException("仅已下达或已批准状态可开始，当前状态：" + subcontract.getStatus());
        }
        subcontract.setStatus(STATUS_IN_PROGRESS);
        subcontract.setActualStartDate(new Date());
        baseMapper.updateById(subcontract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long subcontractId) {
        PrdSubcontract subcontract = baseMapper.selectById(subcontractId);
        if (subcontract == null) {
            throw new ServiceException("委外订单不存在");
        }
        if (!STATUS_IN_PROGRESS.equals(subcontract.getStatus())) {
            throw new ServiceException("仅进行中状态可完工，当前状态：" + subcontract.getStatus());
        }
        subcontract.setStatus(STATUS_COMPLETED);
        subcontract.setActualEndDate(new Date());
        baseMapper.updateById(subcontract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long subcontractId) {
        PrdSubcontract subcontract = baseMapper.selectById(subcontractId);
        if (subcontract == null) {
            throw new ServiceException("委外订单不存在");
        }
        if (STATUS_CLOSED.equals(subcontract.getStatus())) {
            return;
        }
        subcontract.setStatus(STATUS_CLOSED);
        baseMapper.updateById(subcontract);
    }

    private LambdaQueryWrapper<PrdSubcontract> buildQueryWrapper(PrdSubcontractBo bo) {
        LambdaQueryWrapper<PrdSubcontract> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getSubcontractNo()), PrdSubcontract::getSubcontractNo, bo.getSubcontractNo());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), PrdSubcontract::getVendorName, bo.getVendorName());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialCode()), PrdSubcontract::getMaterialCode, bo.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), PrdSubcontract::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), PrdSubcontract::getStatus, bo.getStatus());
        lqw.orderByDesc(PrdSubcontract::getCreateTime);
        return lqw;
    }
}
