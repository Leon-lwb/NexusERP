package com.nexus.erp.prd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdWorkOrderBo;
import com.nexus.erp.prd.domain.vo.PrdWorkOrderVo;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.service.IPrdWorkOrderService;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.mapper.WmProductionIssueLineMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueMapper;
import com.nexus.erp.wm.service.IWmStockOperationService;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产工单Service业务层处理
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@DS("erp")
@Service("prdWorkOrderService")
public class PrdWorkOrderServiceImpl implements IPrdWorkOrderService {

    private final PrdWorkOrderMapper baseMapper;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final PrdBackflushServiceImpl backflushService;
    private final PrdMaterialRequirementMapper requirementMapper;
    private final PrdMaterialRequirementDetailMapper detailMapper;
    private final WmProductionIssueMapper productionIssueMapper;
    private final WmProductionIssueLineMapper productionIssueLineMapper;
    private final IWmStockOperationService stockOperationService;

    @Override
    public TableDataInfo<PrdWorkOrderVo> selectPageList(PrdWorkOrderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PrdWorkOrder> lqw = buildQueryWrapper(bo);
        Page<PrdWorkOrderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<PrdWorkOrderVo> selectList(PrdWorkOrderBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public PrdWorkOrderVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public PrdWorkOrder selectPrdWorkOrderByWoId(Long woId) {
        return baseMapper.selectById(woId);
    }

    @Override
    public List<PrdWorkOrder> selectPrdWorkOrderList(PrdWorkOrder prdWorkOrder) {
        LambdaQueryWrapper<PrdWorkOrder> lqw = buildEntityQueryWrapper(prdWorkOrder);
        return baseMapper.selectList(lqw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertPrdWorkOrder(PrdWorkOrder prdWorkOrder) {
        return baseMapper.insert(prdWorkOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PrdWorkOrderBo bo) {
        PrdWorkOrder entity = MapstructUtils.convert(bo, PrdWorkOrder.class);
        entity.setWoStatus(STATUS_CREATED);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PrdWorkOrderBo bo) {
        PrdWorkOrder entity = MapstructUtils.convert(bo, PrdWorkOrder.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePrdWorkOrder(PrdWorkOrder prdWorkOrder) {
        return baseMapper.updateById(prdWorkOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePrdWorkOrderByWoIds(Long[] woIds) {
        return baseMapper.deleteBatchIds(List.of(woIds));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePrdWorkOrderByWoId(Long woId) {
        return baseMapper.deleteById(woId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long woId) {
        PrdWorkOrder wo = baseMapper.selectById(woId);
        if (wo == null) {
            throw new RuntimeException("工单不存在");
        }
        if (!STATUS_CREATED.equals(wo.getWoStatus()) && !STATUS_APPROVED.equals(wo.getWoStatus())) {
            throw new RuntimeException("仅新建或已批准状态工单可下达,当前状态:" + wo.getWoStatus());
        }
        wo.setWoStatus(STATUS_RELEASED);
        baseMapper.updateById(wo);

        // ★ 新增：工单下达时自动生成生产领料单（排除倒冲物料）
        createProductionIssueOnRelease(wo);
    }

    /**
     * 工单下达时创建生产领料单
     */
    private void createProductionIssueOnRelease(PrdWorkOrder wo) {
        if (wo.getPlanId() == null) {
            return;
        }
        LambdaQueryWrapper<PrdMaterialRequirement> reqLqw = Wrappers.lambdaQuery();
        reqLqw.eq(PrdMaterialRequirement::getPlanId, wo.getPlanId());
        List<PrdMaterialRequirement> reqs = requirementMapper.selectList(reqLqw);
        if (reqs.isEmpty()) {
            return;
        }

        // 创建领料单头
        WmProductionIssue issue = new WmProductionIssue();
        issue.setWoId(wo.getWoId());
        issue.setWoNo(wo.getWoNo());
        issue.setIssueDate(new Date());
        issue.setStatus("CREATED");
        issue.setWarehouseId(wo.getWorkshopId());
        issue.setRemark("工单下达自动生成");
        productionIssueMapper.insert(issue);

        // 创建领料单行（排除倒冲物料）
        boolean hasLines = false;
        for (PrdMaterialRequirement req : reqs) {
            LambdaQueryWrapper<PrdMaterialRequirementDetail> detailLqw = Wrappers.lambdaQuery();
            detailLqw.eq(PrdMaterialRequirementDetail::getRequirementId, req.getRequirementId());
            List<PrdMaterialRequirementDetail> details = detailMapper.selectList(detailLqw);

            for (PrdMaterialRequirementDetail detail : details) {
                // 倒冲物料不生成领料单行（完工时自动倒冲）
                if (detail.getBackflushFlag() != null && detail.getBackflushFlag() == 1) {
                    continue;
                }
                WmProductionIssueLine line = new WmProductionIssueLine();
                line.setIssueId(issue.getIssueId());
                line.setMaterialId(detail.getMaterialId());
                line.setMaterialCode(detail.getMaterialCode());
                line.setMaterialName(detail.getMaterialName());
                line.setMaterialSpec(detail.getMaterialSpec());
                line.setBaseUomId(detail.getUnit());
                line.setQuantity(detail.getRequiredQuantity());
                productionIssueLineMapper.insert(line);
                hasLines = true;
            }
        }

        if (hasLines) {
            log.info("工单[{}]下达后自动生成领料单[{}]", wo.getWoNo(), issue.getIssueCode());
        } else {
            // 无领料行则删除空领料单
            productionIssueMapper.deleteById(issue.getIssueId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startProduction(Long woId) {
        PrdWorkOrder wo = baseMapper.selectById(woId);
        if (wo == null) {
            throw new RuntimeException("工单不存在");
        }
        if (!STATUS_RELEASED.equals(wo.getWoStatus())) {
            throw new RuntimeException("仅已下达状态工单可开始生产,当前状态:" + wo.getWoStatus());
        }

        // ★ 新增：校验非倒冲工单是否已生成领料单
        if (wo.getBackflushFlag() == null || wo.getBackflushFlag() != 1) {
            LambdaQueryWrapper<WmProductionIssue> issueLqw = Wrappers.lambdaQuery();
            issueLqw.eq(WmProductionIssue::getWoId, wo.getWoId());
            long issueCount = productionIssueMapper.selectCount(issueLqw);
            if (issueCount == 0) {
                throw new RuntimeException("工单未生成领料单，请先下达工单或手动创建领料单");
            }
        }

        wo.setWoStatus(STATUS_IN_PROGRESS);
        wo.setActualStartDate(new Date());
        baseMapper.updateById(wo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long woId) {
        PrdWorkOrder wo = baseMapper.selectById(woId);
        if (wo == null) {
            throw new RuntimeException("工单不存在");
        }
        if (!STATUS_IN_PROGRESS.equals(wo.getWoStatus())) {
            throw new RuntimeException("仅生产中状态工单可完工,当前状态:" + wo.getWoStatus());
        }

        // 完工数量：取计划数量（方案A：工单级倒冲）
        BigDecimal completionQty = wo.getPlannedQty() != null ? wo.getPlannedQty() : BigDecimal.ZERO;

        wo.setWoStatus(STATUS_COMPLETED);
        wo.setActualEndDate(new Date());
        wo.setCompletedQty(
            wo.getCompletedQty() != null
                ? wo.getCompletedQty().add(completionQty)
                : completionQty);
        baseMapper.updateById(wo);

        // ★ 新增：完工倒冲领料
        backflushService.backflushOnCompletion(woId, completionQty);

        // ★ 新增：成品入库
        if (wo.getMaterialId() != null && completionQty.compareTo(BigDecimal.ZERO) > 0) {
            stockOperationService.postProductionCompletion(
                woId, wo.getMaterialId(), wo.getMaterialCode(), wo.getMaterialName(),
                completionQty, wo.getWorkshopId(), null, new Date());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long woId) {
        PrdWorkOrder wo = baseMapper.selectById(woId);
        if (wo == null) {
            throw new RuntimeException("工单不存在");
        }
        if (STATUS_CLOSED.equals(wo.getWoStatus())) {
            return;
        }
        if (STATUS_IN_PROGRESS.equals(wo.getWoStatus())) {
            throw new RuntimeException("生产中工单不能直接关闭,请先完工");
        }
        wo.setWoStatus(STATUS_CLOSED);
        baseMapper.updateById(wo);
    }

    // ==================== Flowable 工作流集成 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String woIdStr, String selectFlowId) {
        Long woId = Long.parseLong(woIdStr);
        PrdWorkOrder entity = baseMapper.selectById(woId);
        if (entity == null) {
            throw new RuntimeException("工单不存在");
        }
        if (!STATUS_CREATED.equals(entity.getWoStatus())) {
            throw new RuntimeException("只有新建状态的工单才能提交审批");
        }
        if (StringUtils.isBlank(selectFlowId)) {
            if (entity.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(entity.getSelectFlowId());
            } else {
                throw new RuntimeException("请先在编辑页选择审批流程并保存");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", woIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("woId", entity.getWoId());
        variables.put("woNo", entity.getWoNo());
        R<Void> result = processService.startProcessByDataId(woIdStr, selectFlowId, "prdWorkOrderService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new RuntimeException(result.getMsg());
    }

    @Override
    public String getProcInsId(String woIdStr) {
        try {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(woIdStr);
            if (business != null && StringUtils.isNotBlank(business.getProcessInstanceId())) {
                return business.getProcessInstanceId();
            }
        } catch (Exception ignored) {
        }
        ProcessInstance runtime = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(woIdStr).singleResult();
        if (runtime != null) {
            return runtime.getId();
        }
        HistoricProcessInstance hist = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(woIdStr).singleResult();
        return hist != null ? hist.getId() : null;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterFlowHandle(WfMyBusiness business) {
        Long woId = Long.parseLong(business.getDataId());
        PrdWorkOrder entity = baseMapper.selectById(woId);
        if (entity == null) return;
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            entity.setWoStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            entity.setWoStatus(STATUS_APPROVED);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            entity.setWoStatus(STATUS_CREATED);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            entity.setWoStatus(STATUS_CREATED);
        }
        baseMapper.updateById(entity);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Object getBusinessDataById(String dataId) {
        return selectById(Long.parseLong(dataId));
    }

    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    private LambdaQueryWrapper<PrdWorkOrder> buildQueryWrapper(PrdWorkOrderBo bo) {
        LambdaQueryWrapper<PrdWorkOrder> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getWoNo()), PrdWorkOrder::getWoNo, bo.getWoNo());
        lqw.like(StringUtils.isNotBlank(bo.getPlanName()), PrdWorkOrder::getPlanName, bo.getPlanName());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceType()), PrdWorkOrder::getSourceType, bo.getSourceType());
        lqw.eq(bo.getSourceId() != null, PrdWorkOrder::getSourceId, bo.getSourceId());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceNo()), PrdWorkOrder::getSourceNo, bo.getSourceNo());
        lqw.eq(bo.getSourceLineId() != null, PrdWorkOrder::getSourceLineId, bo.getSourceLineId());
        lqw.eq(bo.getMaterialId() != null, PrdWorkOrder::getMaterialId, bo.getMaterialId());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), PrdWorkOrder::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getWoStatus()), PrdWorkOrder::getWoStatus, bo.getWoStatus());
        lqw.eq(bo.getWorkshopId() != null, PrdWorkOrder::getWorkshopId, bo.getWorkshopId());
        lqw.like(StringUtils.isNotBlank(bo.getWorkshopName()), PrdWorkOrder::getWorkshopName, bo.getWorkshopName());
        lqw.eq(bo.getWorkstationId() != null, PrdWorkOrder::getWorkstationId, bo.getWorkstationId());
        lqw.like(StringUtils.isNotBlank(bo.getWorkstationName()), PrdWorkOrder::getWorkstationName, bo.getWorkstationName());
        lqw.eq(bo.getProductionLineId() != null, PrdWorkOrder::getProductionLineId, bo.getProductionLineId());
        lqw.eq(bo.getResponsibleId() != null, PrdWorkOrder::getResponsibleId, bo.getResponsibleId());
        lqw.like(StringUtils.isNotBlank(bo.getResponsibleName()), PrdWorkOrder::getResponsibleName, bo.getResponsibleName());
        lqw.eq(bo.getTeamId() != null, PrdWorkOrder::getTeamId, bo.getTeamId());
        lqw.like(StringUtils.isNotBlank(bo.getTeamName()), PrdWorkOrder::getTeamName, bo.getTeamName());
        lqw.eq(bo.getPriority() != null, PrdWorkOrder::getPriority, bo.getPriority());
        return lqw;
    }

    private LambdaQueryWrapper<PrdWorkOrder> buildEntityQueryWrapper(PrdWorkOrder entity) {
        LambdaQueryWrapper<PrdWorkOrder> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(entity.getWoNo()), PrdWorkOrder::getWoNo, entity.getWoNo());
        lqw.like(StringUtils.isNotBlank(entity.getPlanName()), PrdWorkOrder::getPlanName, entity.getPlanName());
        lqw.eq(StringUtils.isNotBlank(entity.getSourceType()), PrdWorkOrder::getSourceType, entity.getSourceType());
        lqw.eq(entity.getSourceId() != null, PrdWorkOrder::getSourceId, entity.getSourceId());
        lqw.eq(StringUtils.isNotBlank(entity.getSourceNo()), PrdWorkOrder::getSourceNo, entity.getSourceNo());
        lqw.eq(entity.getSourceLineId() != null, PrdWorkOrder::getSourceLineId, entity.getSourceLineId());
        lqw.eq(entity.getMaterialId() != null, PrdWorkOrder::getMaterialId, entity.getMaterialId());
        lqw.like(StringUtils.isNotBlank(entity.getMaterialName()), PrdWorkOrder::getMaterialName, entity.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(entity.getWoStatus()), PrdWorkOrder::getWoStatus, entity.getWoStatus());
        lqw.eq(entity.getWorkshopId() != null, PrdWorkOrder::getWorkshopId, entity.getWorkshopId());
        lqw.like(StringUtils.isNotBlank(entity.getWorkshopName()), PrdWorkOrder::getWorkshopName, entity.getWorkshopName());
        lqw.eq(entity.getWorkstationId() != null, PrdWorkOrder::getWorkstationId, entity.getWorkstationId());
        lqw.like(StringUtils.isNotBlank(entity.getWorkstationName()), PrdWorkOrder::getWorkstationName, entity.getWorkstationName());
        lqw.eq(entity.getProductionLineId() != null, PrdWorkOrder::getProductionLineId, entity.getProductionLineId());
        lqw.eq(entity.getResponsibleId() != null, PrdWorkOrder::getResponsibleId, entity.getResponsibleId());
        lqw.like(StringUtils.isNotBlank(entity.getResponsibleName()), PrdWorkOrder::getResponsibleName, entity.getResponsibleName());
        lqw.eq(entity.getTeamId() != null, PrdWorkOrder::getTeamId, entity.getTeamId());
        lqw.like(StringUtils.isNotBlank(entity.getTeamName()), PrdWorkOrder::getTeamName, entity.getTeamName());
        lqw.eq(entity.getPriority() != null, PrdWorkOrder::getPriority, entity.getPriority());
        return lqw;
    }
}
