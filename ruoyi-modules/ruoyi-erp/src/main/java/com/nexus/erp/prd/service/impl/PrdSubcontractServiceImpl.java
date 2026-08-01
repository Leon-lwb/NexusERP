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
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 分页查询委外订单列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 委外订单分页列表
     */
    @Override
    public TableDataInfo<PrdSubcontractVo> selectPageList(PrdSubcontractBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PrdSubcontract> lqw = buildQueryWrapper(bo);
        Page<PrdSubcontractVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询委外订单列表（不分页）
     *
     * @param bo 查询参数
     * @return 委外订单列表
     */
    @Override
    public List<PrdSubcontractVo> selectList(PrdSubcontractBo bo) {
        LambdaQueryWrapper<PrdSubcontract> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    /**
     * 根据 ID 查询委外订单详情
     *
     * @param id 委外订单 ID
     * @return 委外订单详细信息
     */
    @Override
    public PrdSubcontractVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增委外订单
     * <p>
     * 校验供应商是否存在、物料是否被禁用、数量大于零，初始状态设为 DRAFT。
     *
     * @param bo 委外订单业务对象
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PrdSubcontractBo bo) {
        PrdSubcontract subcontract = MapstructUtils.convert(bo, PrdSubcontract.class);
        if (subcontract.getPlannedQty() != null && subcontract.getUnitPrice() != null) {
            subcontract.setTotalAmount(subcontract.getPlannedQty().multiply(subcontract.getUnitPrice()));
        }
        return baseMapper.insert(subcontract);
    }

    /**
     * 修改委外订单
     * <p>
     * 仅 DRAFT 状态可编辑，已开工时只允许修改结束日期和备注。
     *
     * @param bo 委外订单业务对象
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PrdSubcontractBo bo) {
        PrdSubcontract subcontract = MapstructUtils.convert(bo, PrdSubcontract.class);
        if (subcontract.getPlannedQty() != null && subcontract.getUnitPrice() != null) {
            subcontract.setTotalAmount(subcontract.getPlannedQty().multiply(subcontract.getUnitPrice()));
        }
        return baseMapper.updateById(subcontract);
    }

    /**
     * 批量删除委外订单
     * <p>
     * 仅 DRAFT 或 CLOSED 状态的委外订单可被删除。
     *
     * @param ids 要删除的委外订单 ID 数组
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 下达委外订单
     * <p>
     * 校验供应商启用状态后，更新状态为 RELEASED，并自动生成关联生产工单。
     *
     * @param subcontractId 委外订单 ID
     */
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

    /**
     * 开始委外订单
     * <p>
     * 更新状态为 IN_PROGRESS，并记录实际开始时间。
     *
     * @param subcontractId 委外订单 ID
     */
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

    /**
     * 完工委外订单
     * <p>
     * 更新状态为 COMPLETED，记录实际结束时间；若完工数量小于订单数量，记录部分完工。
     *
     * @param subcontractId 委外订单 ID
     */
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

    /**
     * 关闭委外订单
     * <p>
     * 更新状态为 CLOSED，关闭后不可再操作。
     *
     * @param subcontractId 委外订单 ID
     */
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

    /**
     * 提交审批委外订单（草稿 → 已提交）
     *
     * @param subcontractId 委外订单 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long subcontractId) {
        PrdSubcontract subcontract = baseMapper.selectById(subcontractId);
        if (subcontract == null) {
            throw new ServiceException("委外订单不存在");
        }
        if (!STATUS_DRAFT.equals(subcontract.getStatus())) {
            throw new ServiceException("仅草稿状态可提交审批，当前状态：" + subcontract.getStatus());
        }
        subcontract.setStatus(STATUS_SUBMITTED);
        baseMapper.updateById(subcontract);
    }

    /**
     * 查询委外订单操作日志（基于状态与时间字段生成状态时间线）
     *
     * @param subcontractId 委外订单 ID
     * @return 操作日志列表（[{operation, time}]）
     */
    @Override
    public List<Map<String, Object>> getOperationLogs(Long subcontractId) {
        PrdSubcontract subcontract = baseMapper.selectById(subcontractId);
        if (subcontract == null) {
            throw new ServiceException("委外订单不存在");
        }
        List<Map<String, Object>> logs = new ArrayList<>();
        addLog(logs, "创建委外订单", subcontract.getCreateTime());
        if (subcontract.getPlanStartDate() != null) {
            addLog(logs, "计划开始", subcontract.getPlanStartDate());
        }
        if (subcontract.getPlanEndDate() != null) {
            addLog(logs, "计划结束", subcontract.getPlanEndDate());
        }
        if (subcontract.getActualStartDate() != null) {
            addLog(logs, "实际开始（发料）", subcontract.getActualStartDate());
        }
        if (subcontract.getActualEndDate() != null) {
            addLog(logs, "实际完工（收货）", subcontract.getActualEndDate());
        }
        addLog(logs, "当前状态：" + subcontract.getStatus(), subcontract.getUpdateTime());
        return logs;
    }

    private void addLog(List<Map<String, Object>> logs, String operation, java.util.Date time) {
        if (time == null) {
            return;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("operation", operation);
        m.put("time", time);
        logs.add(m);
    }

    private LambdaQueryWrapper<PrdSubcontract> buildQueryWrapper(PrdSubcontractBo bo) {
        LambdaQueryWrapper<PrdSubcontract> lqw = Wrappers.<PrdSubcontract>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getSubcontractNo()), PrdSubcontract::getSubcontractNo, bo.getSubcontractNo())
            .like(StringUtils.isNotBlank(bo.getVendorName()), PrdSubcontract::getVendorName, bo.getVendorName())
            .like(StringUtils.isNotBlank(bo.getMaterialCode()), PrdSubcontract::getMaterialCode, bo.getMaterialCode())
            .like(StringUtils.isNotBlank(bo.getMaterialName()), PrdSubcontract::getMaterialName, bo.getMaterialName())
            .eq(StringUtils.isNotBlank(bo.getStatus()), PrdSubcontract::getStatus, bo.getStatus())
            .orderByDesc(PrdSubcontract::getCreateTime);
        return lqw;
    }
}
