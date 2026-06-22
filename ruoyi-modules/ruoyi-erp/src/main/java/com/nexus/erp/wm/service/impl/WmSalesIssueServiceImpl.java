package com.nexus.erp.wm.service.impl;

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
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmSalesIssue;
import com.nexus.erp.wm.domain.WmSalesIssueLine;
import com.nexus.erp.wm.domain.bo.WmSalesIssueBo;
import com.nexus.erp.wm.domain.bo.WmSalesIssueLineBo;
import com.nexus.erp.wm.domain.vo.WmSalesIssueVo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmSalesIssueLineMapper;
import com.nexus.erp.wm.mapper.WmSalesIssueMapper;
import com.nexus.erp.wm.service.IWmSalesIssueService;
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

import com.baomidou.dynamic.datasource.annotation.DS;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售出库Service业务层处理
 *
 * @author nexus
 * @date 2026-04-03
 */
@RequiredArgsConstructor
@DS("erp")
@Service("wmSalesIssueService")
public class WmSalesIssueServiceImpl implements IWmSalesIssueService {

    private final WmSalesIssueMapper baseMapper;
    private final WmSalesIssueLineMapper lineMapper;
    private final WmMaterialStockMapper wmMaterialStockMapper;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final AutoCodeUtil autoCodeUtil;

    @Override
    public TableDataInfo<WmSalesIssueVo> selectPageList(WmSalesIssueBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmSalesIssue> lqw = buildQueryWrapper(bo);
        Page<WmSalesIssueVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmSalesIssueVo> selectList(WmSalesIssueBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmSalesIssueVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmSalesIssueBo bo) {
        WmSalesIssue entity = MapstructUtils.convert(bo, WmSalesIssue.class);
        if (StringUtils.isBlank(entity.getIssueCode())) {
            entity.setIssueCode(autoCodeUtil.genSerialCode("WM_SALES_ISSUE_CODE", null));
        }
        int rows = baseMapper.insert(entity);
        insertLines(entity.getIssueId(), bo.getWmSalesIssueLineList());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmSalesIssueBo bo) {
        WmSalesIssue entity = MapstructUtils.convert(bo, WmSalesIssue.class);
        lineMapper.delete(new LambdaQueryWrapper<WmSalesIssueLine>()
            .eq(WmSalesIssueLine::getIssueId, entity.getIssueId()));
        insertLines(entity.getIssueId(), bo.getWmSalesIssueLineList());
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        lineMapper.delete(new LambdaQueryWrapper<WmSalesIssueLine>()
            .in(WmSalesIssueLine::getIssueId, List.of(ids)));
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int confirmSalesIssue(Long issueId) {
        WmSalesIssue salesIssue = baseMapper.selectById(issueId);
        if (salesIssue == null) {
            throw new RuntimeException("销售出库单不存在");
        }
        if (!STATUS_DRAFT.equals(salesIssue.getStatus()) && !STATUS_APPROVED.equals(salesIssue.getStatus())) {
            throw new RuntimeException("只有草稿或已批准的出库单才能确认");
        }
        salesIssue.setStatus(STATUS_CONFIRMED);
        return baseMapper.updateById(salesIssue);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int completeSalesIssue(Long issueId) {
        WmSalesIssue salesIssue = baseMapper.selectById(issueId);
        if (salesIssue == null) {
            throw new RuntimeException("销售出库单不存在");
        }
        if (!STATUS_CONFIRMED.equals(salesIssue.getStatus())) {
            throw new RuntimeException("只有已确认的出库单才能完成");
        }

        List<WmSalesIssueLine> lineList = lineMapper.selectList(
            new LambdaQueryWrapper<WmSalesIssueLine>()
                .eq(WmSalesIssueLine::getIssueId, issueId));
        if (lineList == null || lineList.isEmpty()) {
            throw new RuntimeException("出库单明细为空");
        }

        // 扣减库存：按批次效期、收货时间、库存ID排序，跨库存记录 FIFO 扣减
        for (WmSalesIssueLine line : lineList) {
            BigDecimal issueQty = line.getQuantityIssue() != null ? line.getQuantityIssue() : BigDecimal.ZERO;
            if (issueQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            LambdaQueryWrapper<WmMaterialStock> stockLqw = Wrappers.lambdaQuery();
            stockLqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
            stockLqw.eq(WmMaterialStock::getWarehouseId, salesIssue.getWarehouseId());
            stockLqw.eq(line.getLocationId() != null, WmMaterialStock::getLocationId, line.getLocationId());
            stockLqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
            stockLqw.gt(WmMaterialStock::getStockQuantity, BigDecimal.ZERO);
            stockLqw.orderByAsc(WmMaterialStock::getExpireDate);
            stockLqw.orderByAsc(WmMaterialStock::getRecptDate);
            stockLqw.orderByAsc(WmMaterialStock::getStockId);

            List<WmMaterialStock> stockList = wmMaterialStockMapper.selectList(stockLqw);
            if (stockList == null || stockList.isEmpty()) {
                throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不存在");
            }

            BigDecimal remaining = issueQty;
            BigDecimal totalAvailable = BigDecimal.ZERO;
            for (WmMaterialStock stock : stockList) {
                totalAvailable = totalAvailable.add(stock.getStockQuantity() != null ? stock.getStockQuantity() : BigDecimal.ZERO);
            }
            if (totalAvailable.compareTo(issueQty) < 0) {
                throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不足，当前库存："
                    + totalAvailable + "，出库数量：" + issueQty);
            }

            for (WmMaterialStock stock : stockList) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal currentQty = stock.getStockQuantity() != null ? stock.getStockQuantity() : BigDecimal.ZERO;
                if (currentQty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal consumeQty = currentQty.min(remaining);
                stock.setStockQuantity(currentQty.subtract(consumeQty));
                wmMaterialStockMapper.updateById(stock);
                remaining = remaining.subtract(consumeQty);
            }
        }

        salesIssue.setStatus(STATUS_COMPLETED);
        return baseMapper.updateById(salesIssue);
    }

    // ==================== Flowable 工作流集成 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String issueIdStr, String selectFlowId) {
        Long issueId = Long.parseLong(issueIdStr);
        WmSalesIssue entity = baseMapper.selectById(issueId);
        if (entity == null) {
            throw new RuntimeException("销售出库单不存在");
        }
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new RuntimeException("只有草稿状态的出库单才能提交审批");
        }
        if (StringUtils.isBlank(selectFlowId)) {
            if (entity.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(entity.getSelectFlowId());
            } else {
                throw new RuntimeException("请先在编辑页选择审批流程并保存");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", issueIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("issueId", entity.getIssueId());
        variables.put("issueCode", entity.getIssueCode());
        variables.put("clientName", entity.getClientName());
        R<Void> result = processService.startProcessByDataId(issueIdStr, selectFlowId, "wmSalesIssueService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new RuntimeException(result.getMsg());
    }

    @Override
    public String getProcInsId(String issueIdStr) {
        try {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(issueIdStr);
            if (business != null && StringUtils.isNotBlank(business.getProcessInstanceId())) {
                return business.getProcessInstanceId();
            }
        } catch (Exception ignored) {
        }
        ProcessInstance runtime = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(issueIdStr).singleResult();
        if (runtime != null) {
            return runtime.getId();
        }
        HistoricProcessInstance hist = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(issueIdStr).singleResult();
        return hist != null ? hist.getId() : null;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterFlowHandle(WfMyBusiness business) {
        Long issueId = Long.parseLong(business.getDataId());
        WmSalesIssue entity = baseMapper.selectById(issueId);
        if (entity == null) return;
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            entity.setStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            entity.setStatus(STATUS_APPROVED);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            entity.setStatus(STATUS_REJECTED);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            entity.setStatus(STATUS_DRAFT);
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

    private void insertLines(Long issueId, List<WmSalesIssueLineBo> lineBoList) {
        if (lineBoList != null && !lineBoList.isEmpty()) {
            List<WmSalesIssueLine> lineList = MapstructUtils.convert(lineBoList, WmSalesIssueLine.class);
            for (WmSalesIssueLine line : lineList) {
                line.setLineId(null);
                line.setIssueId(issueId);
            }
            lineMapper.insertBatch(lineList);
        }
    }

    private LambdaQueryWrapper<WmSalesIssue> buildQueryWrapper(WmSalesIssueBo bo) {
        LambdaQueryWrapper<WmSalesIssue> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getIssueCode()), WmSalesIssue::getIssueCode, bo.getIssueCode());
        lqw.like(StringUtils.isNotBlank(bo.getIssueName()), WmSalesIssue::getIssueName, bo.getIssueName());
        lqw.eq(bo.getSoId() != null, WmSalesIssue::getSoId, bo.getSoId());
        lqw.eq(StringUtils.isNotBlank(bo.getSoNo()), WmSalesIssue::getSoNo, bo.getSoNo());
        lqw.eq(bo.getClientId() != null, WmSalesIssue::getClientId, bo.getClientId());
        lqw.eq(StringUtils.isNotBlank(bo.getClientCode()), WmSalesIssue::getClientCode, bo.getClientCode());
        lqw.like(StringUtils.isNotBlank(bo.getClientName()), WmSalesIssue::getClientName, bo.getClientName());
        lqw.eq(bo.getWarehouseId() != null, WmSalesIssue::getWarehouseId, bo.getWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getWarehouseCode()), WmSalesIssue::getWarehouseCode, bo.getWarehouseCode());
        lqw.like(StringUtils.isNotBlank(bo.getWarehouseName()), WmSalesIssue::getWarehouseName, bo.getWarehouseName());
        lqw.eq(bo.getIssueDate() != null, WmSalesIssue::getIssueDate, bo.getIssueDate());
        lqw.eq(bo.getTotalQuantity() != null, WmSalesIssue::getTotalQuantity, bo.getTotalQuantity());
        lqw.eq(bo.getTotalAmount() != null, WmSalesIssue::getTotalAmount, bo.getTotalAmount());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmSalesIssue::getStatus, bo.getStatus());
        return lqw;
    }

}
