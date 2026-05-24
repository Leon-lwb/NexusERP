package org.dromara.erp.pu.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.flowable.core.domain.ActStatus;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.pu.domain.PuPurchaseReturn;
import org.dromara.erp.pu.domain.PuPurchaseReturnLine;
import org.dromara.erp.pu.domain.bo.PuPurchaseReturnBo;
import org.dromara.erp.pu.domain.vo.PuPurchaseReturnVo;
import org.dromara.erp.pu.mapper.PuPurchaseReturnLineMapper;
import org.dromara.erp.pu.mapper.PuPurchaseReturnMapper;
import org.dromara.erp.pu.service.IPuPurchaseReturnService;
import org.dromara.erp.wm.domain.WmMaterialStock;
import org.dromara.erp.wm.domain.WmMaterialStockTx;
import org.dromara.erp.wm.mapper.WmMaterialStockMapper;
import org.dromara.erp.wm.mapper.WmMaterialStockTxMapper;
import org.dromara.system.strategy.AutoCodeUtil;
import org.dromara.workflow.domain.WfMyBusiness;
import org.dromara.workflow.service.IWfProcessService;
import org.dromara.workflow.service.impl.WfMyBusinessServiceImpl;
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

/**
 * 采购退货服务实现类
 *
 * @author ruoyi
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("puPurchaseReturnService")
public class PuPurchaseReturnServiceImpl implements IPuPurchaseReturnService {

    private final PuPurchaseReturnMapper baseMapper;
    private final PuPurchaseReturnLineMapper lineMapper;
    private final WmMaterialStockMapper stockMapper;
    private final WmMaterialStockTxMapper stockTxMapper;
    private final AutoCodeUtil autoCodeUtil;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    @Override
    public TableDataInfo<PuPurchaseReturnVo> selectPageList(PuPurchaseReturnBo bo, PageQuery pageQuery) {
        Page<PuPurchaseReturnVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        return TableDataInfo.build(page);
    }

    @Override
    public List<PuPurchaseReturnVo> selectList(PuPurchaseReturnBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public PuPurchaseReturnVo selectById(Long id) {
        PuPurchaseReturnVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getReturnId() != null) {
            LambdaQueryWrapper<PuPurchaseReturnLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(PuPurchaseReturnLine::getReturnId, vo.getReturnId());
            lqw.orderByAsc(PuPurchaseReturnLine::getSortOrder);
            vo.setPuPurchaseReturnLineList(lineMapper.selectList(lqw));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PuPurchaseReturnBo bo) {
        PuPurchaseReturn entity = MapstructUtils.convert(bo, PuPurchaseReturn.class);
        if (StringUtils.isBlank(entity.getReturnNo())) {
            entity.setReturnNo(autoCodeUtil.genSerialCode("PU_RETURN_CODE", null));
        }
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus(STATUS_DRAFT);
        }
        if (entity.getReturnDate() == null) {
            entity.setReturnDate(new Date());
        }
        int rows = baseMapper.insert(entity);
        insertLines(entity.getReturnId(), bo.getPuPurchaseReturnLineList());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PuPurchaseReturnBo bo) {
        PuPurchaseReturn entity = MapstructUtils.convert(bo, PuPurchaseReturn.class);
        PuPurchaseReturn current = baseMapper.selectById(entity.getReturnId());
        if (current == null) {
            throw new RuntimeException("采购退货单不存在");
        }
        if (!STATUS_DRAFT.equals(current.getStatus())) {
            throw new RuntimeException("只有草稿状态的采购退货单才能编辑");
        }
        deleteLines(entity.getReturnId());
        insertLines(entity.getReturnId(), bo.getPuPurchaseReturnLineList());
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteLines(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long returnId) {
        PuPurchaseReturn purchaseReturn = ensureReturn(returnId);
        if (!STATUS_DRAFT.equals(purchaseReturn.getStatus())) {
            throw new RuntimeException("只有草稿状态的采购退货单才能确认");
        }
        purchaseReturn.setStatus(STATUS_CONFIRMED);
        baseMapper.updateById(purchaseReturn);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long returnId) {
        PuPurchaseReturn purchaseReturn = ensureReturn(returnId);
        if (STATUS_EXECUTED.equals(purchaseReturn.getStatus())) {
            return;
        }
        List<PuPurchaseReturnLine> lines = lineMapper.selectList(Wrappers.lambdaQuery(PuPurchaseReturnLine.class).eq(PuPurchaseReturnLine::getReturnId, returnId));
        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("采购退货单无明细行");
        }
        for (PuPurchaseReturnLine line : lines) {
            postReturnOut(purchaseReturn, line, new Date());
        }
        purchaseReturn.setStatus(STATUS_EXECUTED);
        baseMapper.updateById(purchaseReturn);
    }

    // ==================== Flowable 工作流集成 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String returnIdStr, String selectFlowId) {
        Long returnId = Long.parseLong(returnIdStr);
        PuPurchaseReturn entity = baseMapper.selectById(returnId);
        if (entity == null) {
            throw new RuntimeException("采购退货单不存在");
        }
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new RuntimeException("只有草稿状态的采购退货单才能提交审批");
        }
        if (StringUtils.isBlank(selectFlowId)) {
            if (entity.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(entity.getSelectFlowId());
            } else {
                throw new RuntimeException("请先在编辑页选择审批流程并保存");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", returnIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("returnId", entity.getReturnId());
        variables.put("returnNo", entity.getReturnNo());
        R<Void> result = processService.startProcessByDataId(returnIdStr, selectFlowId, "puPurchaseReturnService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new RuntimeException(result.getMsg());
    }

    @Override
    public String getProcInsId(String returnIdStr) {
        try {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(returnIdStr);
            if (business != null && StringUtils.isNotBlank(business.getProcessInstanceId())) {
                return business.getProcessInstanceId();
            }
        } catch (Exception ignored) {
        }
        ProcessInstance runtime = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(returnIdStr).singleResult();
        if (runtime != null) {
            return runtime.getId();
        }
        HistoricProcessInstance hist = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(returnIdStr).singleResult();
        return hist != null ? hist.getId() : null;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterFlowHandle(WfMyBusiness business) {
        Long returnId = Long.parseLong(business.getDataId());
        PuPurchaseReturn entity = baseMapper.selectById(returnId);
        if (entity == null) return;
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            entity.setStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            entity.setStatus(STATUS_CONFIRMED);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            entity.setStatus(STATUS_DRAFT);
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

    private void postReturnOut(PuPurchaseReturn purchaseReturn, PuPurchaseReturnLine line, Date now) {
        if (line.getReturnQuantity() == null || line.getReturnQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("退货数量必须大于0");
        }
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        WmMaterialStock stock = stockMapper.selectOne(lqw);
        if (stock == null || stock.getStockQuantity() == null || stock.getStockQuantity().compareTo(line.getReturnQuantity()) < 0) {
            throw new RuntimeException("库存不足，无法执行采购退货");
        }
        BigDecimal beforeQuantity = stock.getStockQuantity();
        BigDecimal afterQuantity = beforeQuantity.subtract(line.getReturnQuantity());
        stock.setStockQuantity(afterQuantity);
        stock.setRecptDate(now);
        stockMapper.updateById(stock);

        WmMaterialStockTx tx = new WmMaterialStockTx();
        tx.setTxType("PUR_RETURN_OUT");
        tx.setMaterialId(stock.getMaterialId());
        tx.setMaterialCode(stock.getMaterialCode());
        tx.setMaterialName(stock.getMaterialName());
        tx.setMaterialSpec(stock.getMaterialSpec());
        tx.setBaseUomId(stock.getBaseUomId());
        tx.setBaseUomName(stock.getBaseUomName());
        tx.setBatchId(stock.getBatchId());
        tx.setBatchCode(stock.getBatchCode());
        tx.setWarehouseId(stock.getWarehouseId());
        tx.setWarehouseCode(stock.getWarehouseCode());
        tx.setWarehouseName(stock.getWarehouseName());
        tx.setLocationId(stock.getLocationId());
        tx.setLocationCode(stock.getLocationCode());
        tx.setLocationName(stock.getLocationName());
        tx.setMaterialStockId(stock.getStockId());
        tx.setTxFlag(-1);
        tx.setTxQuantity(line.getReturnQuantity());
        tx.setBeforeQuantity(beforeQuantity);
        tx.setAfterQuantity(afterQuantity);
        tx.setTxDate(now);
        tx.setSourceDocType("PU_PURCHASE_RETURN");
        tx.setSourceDocId(purchaseReturn.getReturnId());
        tx.setSourceDocCode(purchaseReturn.getReturnNo());
        tx.setSourceDocLineId(line.getLineId());
        tx.setRemark("采购退货出库，采购单号：" + purchaseReturn.getOrderNo());
        stockTxMapper.insert(tx);
    }

    private void insertLines(Long returnId, List<PuPurchaseReturnLine> lineList) {
        if (returnId == null || lineList == null || lineList.isEmpty()) {
            return;
        }
        for (PuPurchaseReturnLine line : lineList) {
            line.setReturnId(returnId);
            lineMapper.insert(line);
        }
    }

    private void deleteLines(Long returnId) {
        if (returnId == null) {
            return;
        }
        lineMapper.delete(Wrappers.lambdaQuery(PuPurchaseReturnLine.class).eq(PuPurchaseReturnLine::getReturnId, returnId));
    }

    private PuPurchaseReturn ensureReturn(Long returnId) {
        PuPurchaseReturn purchaseReturn = baseMapper.selectById(returnId);
        if (purchaseReturn == null) {
            throw new RuntimeException("采购退货单不存在");
        }
        return purchaseReturn;
    }

    private LambdaQueryWrapper<PuPurchaseReturn> buildQueryWrapper(PuPurchaseReturnBo bo) {
        LambdaQueryWrapper<PuPurchaseReturn> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getReturnNo()), PuPurchaseReturn::getReturnNo, bo.getReturnNo());
        lqw.eq(StringUtils.isNotBlank(bo.getOrderNo()), PuPurchaseReturn::getOrderNo, bo.getOrderNo());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), PuPurchaseReturn::getStatus, bo.getStatus());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), PuPurchaseReturn::getVendorName, bo.getVendorName());
        lqw.orderByDesc(PuPurchaseReturn::getCreateTime);
        return lqw;
    }
}
