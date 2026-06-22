package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmStockTakingLine;
import com.nexus.erp.wm.domain.WmStockTakingPlan;
import com.nexus.erp.wm.domain.bo.WmStockTakingPlanBo;
import com.nexus.erp.wm.domain.vo.WmStockTakingPlanVo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmStockTakingLineMapper;
import com.nexus.erp.wm.mapper.WmStockTakingPlanMapper;
import com.nexus.erp.wm.service.IWmStockOperationService;
import com.nexus.erp.wm.service.IWmStockTakingPlanService;
import com.nexus.system.strategy.AutoCodeUtil;
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

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("wmStockTakingPlanService")
public class WmStockTakingPlanServiceImpl implements IWmStockTakingPlanService {

    private final WmStockTakingPlanMapper baseMapper;
    private final WmStockTakingLineMapper lineMapper;
    private final WmMaterialStockMapper stockMapper;
    private final IWmStockOperationService stockOperationService;
    private final AutoCodeUtil autoCodeUtil;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    @Override
    public TableDataInfo<WmStockTakingPlanVo> selectPageList(WmStockTakingPlanBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmStockTakingPlan> lqw = buildQueryWrapper(bo);
        Page<WmStockTakingPlanVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmStockTakingPlanVo> selectList(WmStockTakingPlanBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmStockTakingPlanVo selectById(Long id) {
        WmStockTakingPlanVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getPlanId() != null) {
            LambdaQueryWrapper<WmStockTakingLine> lineLqw = Wrappers.lambdaQuery();
            lineLqw.eq(WmStockTakingLine::getPlanId, vo.getPlanId());
            vo.setWmStockTakingLineList(lineMapper.selectList(lineLqw));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmStockTakingPlanBo bo) {
        WmStockTakingPlan entity = MapstructUtils.convert(bo, WmStockTakingPlan.class);
        if (StringUtils.isBlank(entity.getPlanCode())) {
            entity.setPlanCode(autoCodeUtil.genSerialCode("WM_STOCKTAKING_CODE", null));
        }
        int rows = baseMapper.insert(entity);
        if (bo.getWmStockTakingLineList() != null) {
            for (WmStockTakingLine line : bo.getWmStockTakingLineList()) {
                line.setLineId(null);
                line.setPlanId(entity.getPlanId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmStockTakingPlanBo bo) {
        WmStockTakingPlan entity = MapstructUtils.convert(bo, WmStockTakingPlan.class);
        deleteLinesByPlanId(entity.getPlanId());
        if (bo.getWmStockTakingLineList() != null) {
            for (WmStockTakingLine line : bo.getWmStockTakingLineList()) {
                line.setLineId(null);
                line.setPlanId(entity.getPlanId());
                lineMapper.insert(line);
            }
        }
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteLinesByPlanId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateLines(Long planId) {
        WmStockTakingPlan plan = baseMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("盘点计划不存在");
        }
        // 先清除已有盘点行
        deleteLinesByPlanId(planId);

        // 从库存表抓取当前库存快照生成盘点行
        LambdaQueryWrapper<WmMaterialStock> stockLqw = Wrappers.lambdaQuery();
        stockLqw.eq(plan.getWarehouseId() != null, WmMaterialStock::getWarehouseId, plan.getWarehouseId());
        stockLqw.eq(plan.getAreaId() != null, WmMaterialStock::getAreaId, plan.getAreaId());
        stockLqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        stockLqw.gt(WmMaterialStock::getStockQuantity, BigDecimal.ZERO);
        List<WmMaterialStock> stocks = stockMapper.selectList(stockLqw);

        if (stocks == null || stocks.isEmpty()) {
            throw new RuntimeException("所选仓库/库区没有可用库存");
        }

        for (WmMaterialStock stock : stocks) {
            WmStockTakingLine line = new WmStockTakingLine();
            line.setPlanId(planId);
            line.setMaterialId(stock.getMaterialId());
            line.setMaterialCode(stock.getMaterialCode());
            line.setMaterialName(stock.getMaterialName());
            line.setMaterialSpec(stock.getMaterialSpec());
            line.setBaseUomId(stock.getBaseUomId());
            line.setBaseUomName(stock.getBaseUomName());
            line.setBatchId(stock.getBatchId());
            line.setBatchCode(stock.getBatchCode());
            line.setLocationId(stock.getLocationId());
            line.setLocationCode(stock.getLocationCode());
            line.setLocationName(stock.getLocationName());
            line.setStockId(stock.getStockId());
            line.setBookQuantity(stock.getStockQuantity());
            line.setActualQuantity(BigDecimal.ZERO);
            line.setDifferenceQuantity(BigDecimal.ZERO);
            line.setStatus("PENDING");
            lineMapper.insert(line);
        }

        plan.setStatus("GENERATED");
        baseMapper.updateById(plan);
        log.info("盘点计划 {} 生成盘点行完成，共 {} 条", planId, stocks.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postAdjustment(Long planId) {
        WmStockTakingPlan plan = baseMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("盘点计划不存在");
        }

        List<WmStockTakingLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(WmStockTakingLine.class)
                .eq(WmStockTakingLine::getPlanId, planId));

        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("盘点行不存在，请先生成盘点行");
        }

        for (WmStockTakingLine line : lines) {
            BigDecimal actual = line.getActualQuantity() == null ? BigDecimal.ZERO : line.getActualQuantity();
            BigDecimal book = line.getBookQuantity() == null ? BigDecimal.ZERO : line.getBookQuantity();
            BigDecimal diff = actual.subtract(book);

            if (diff.compareTo(BigDecimal.ZERO) != 0) {
                stockOperationService.postCountAdjust(line, actual, book, diff, new Date());
                line.setDifferenceQuantity(diff);
                line.setStatus("POSTED");
            } else {
                line.setDifferenceQuantity(BigDecimal.ZERO);
                line.setStatus("BALANCED");
            }
            lineMapper.updateById(line);
        }

        plan.setStatus("POSTED");
        baseMapper.updateById(plan);
        log.info("盘点计划 {} 差异过账完成", planId);
    }

    // ==================== Flowable 工作流集成 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String planIdStr, String selectFlowId) {
        Long planId = Long.parseLong(planIdStr);
        WmStockTakingPlan plan = baseMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("盘点计划不存在");
        }
        if (StringUtils.isBlank(selectFlowId)) {
            if (plan.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(plan.getSelectFlowId());
            } else {
                throw new RuntimeException("请先在编辑页选择审批流程并保存");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", planIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("planId", plan.getPlanId());
        variables.put("planCode", plan.getPlanCode());
        variables.put("planName", plan.getPlanName());
        variables.put("warehouseName", plan.getWarehouseName());
        R<Void> result = processService.startProcessByDataId(planIdStr, selectFlowId, "wmStockTakingPlanService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new RuntimeException(result.getMsg());
    }

    @Override
    public String getProcInsId(String planIdStr) {
        try {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(planIdStr);
            if (business != null && StringUtils.isNotBlank(business.getProcessInstanceId())) {
                return business.getProcessInstanceId();
            }
        } catch (Exception ignored) {
        }
        ProcessInstance runtime = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(planIdStr).singleResult();
        if (runtime != null) {
            return runtime.getId();
        }
        HistoricProcessInstance hist = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(planIdStr).singleResult();
        return hist != null ? hist.getId() : null;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterFlowHandle(WfMyBusiness business) {
        Long planId = Long.parseLong(business.getDataId());
        WmStockTakingPlan plan = baseMapper.selectById(planId);
        if (plan == null) return;
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            plan.setStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            // 审批通过：执行盘点差异调整，设置状态为已完成
            executeCountAdjust(planId);
            plan.setStatus(STATUS_COMPLETED);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            plan.setStatus(STATUS_REJECTED);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            plan.setStatus(STATUS_DRAFT);
        }
        baseMapper.updateById(plan);
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

    // ==================== 私有方法 ====================

    /**
     * 执行盘点差异调整（审批通过后调用）
     */
    private void executeCountAdjust(Long planId) {
        List<WmStockTakingLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(WmStockTakingLine.class)
                .eq(WmStockTakingLine::getPlanId, planId));

        if (lines == null || lines.isEmpty()) {
            return;
        }

        for (WmStockTakingLine line : lines) {
            BigDecimal actual = line.getActualQuantity() == null ? BigDecimal.ZERO : line.getActualQuantity();
            BigDecimal book = line.getBookQuantity() == null ? BigDecimal.ZERO : line.getBookQuantity();
            BigDecimal diff = actual.subtract(book);

            if (diff.compareTo(BigDecimal.ZERO) != 0) {
                stockOperationService.postCountAdjust(line, actual, book, diff, new Date());
                line.setDifferenceQuantity(diff);
                line.setStatus("POSTED");
            } else {
                line.setDifferenceQuantity(BigDecimal.ZERO);
                line.setStatus("BALANCED");
            }
            lineMapper.updateById(line);
        }
        log.info("盘点计划 {} 审批通过，差异调整完成", planId);
    }

    private void deleteLinesByPlanId(Long planId) {
        if (planId != null) {
            LambdaQueryWrapper<WmStockTakingLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(WmStockTakingLine::getPlanId, planId);
            lineMapper.delete(lqw);
        }
    }

    private LambdaQueryWrapper<WmStockTakingPlan> buildQueryWrapper(WmStockTakingPlanBo bo) {
        LambdaQueryWrapper<WmStockTakingPlan> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getPlanCode()), WmStockTakingPlan::getPlanCode, bo.getPlanCode());
        lqw.like(StringUtils.isNotBlank(bo.getPlanName()), WmStockTakingPlan::getPlanName, bo.getPlanName());
        lqw.eq(bo.getWarehouseId() != null, WmStockTakingPlan::getWarehouseId, bo.getWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmStockTakingPlan::getStatus, bo.getStatus());
        lqw.orderByDesc(WmStockTakingPlan::getCreateTime);
        return lqw;
    }
}
