package com.nexus.erp.so.service.impl;

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
import com.nexus.erp.so.domain.SoReturn;
import com.nexus.erp.so.domain.SoReturnLine;
import com.nexus.erp.so.domain.bo.SoReturnBo;
import com.nexus.erp.so.domain.vo.SoReturnVo;
import com.nexus.erp.so.mapper.SoReturnLineMapper;
import com.nexus.erp.so.mapper.SoReturnMapper;
import com.nexus.erp.so.service.ISoReturnService;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
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

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售退货Service业务层处理
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("soReturnService")
public class SoReturnServiceImpl implements ISoReturnService {

    private final SoReturnMapper baseMapper;
    private final SoReturnLineMapper lineMapper;
    private final WmMaterialStockMapper stockMapper;
    private final WmMaterialStockTxMapper stockTxMapper;
    private final AutoCodeUtil autoCodeUtil;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    /**
     * 分页查询销售退货列表
     */
    @Override
    public TableDataInfo<SoReturnVo> selectPageList(SoReturnBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoReturn> lqw = buildQueryWrapper(bo);
        Page<SoReturnVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询销售退货列表
     */
    @Override
    public List<SoReturnVo> selectList(SoReturnBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询销售退货详情（包含明细行）
     */
    @Override
    public SoReturnVo selectById(Long id) {
        SoReturnVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getReturnId() != null) {
            LambdaQueryWrapper<SoReturnLine> lineLqw = Wrappers.lambdaQuery();
            lineLqw.eq(SoReturnLine::getReturnId, vo.getReturnId());
            lineLqw.orderByAsc(SoReturnLine::getSortOrder);
            vo.setSoReturnLineList(lineMapper.selectList(lineLqw));
        }
        return vo;
    }

    /**
     * 新增销售退货（自动生成退货单号并保存明细行）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoReturnBo bo) {
        SoReturn entity = MapstructUtils.convert(bo, SoReturn.class);
        entity.setReturnNo(autoCodeUtil.genSerialCode("SO_RETURN_CODE", null));
        int rows = baseMapper.insert(entity);
        insertOrUpdateLines(entity.getReturnId(), bo.getSoReturnLineList());
        return rows;
    }

    /**
     * 修改销售退货（先删除原明细行再重新插入）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoReturnBo bo) {
        SoReturn entity = MapstructUtils.convert(bo, SoReturn.class);
        deleteLinesByReturnId(entity.getReturnId());
        insertOrUpdateLines(entity.getReturnId(), bo.getSoReturnLineList());
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除销售退货（同时删除明细行）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteLinesByReturnId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 确认销售退货单（仅草稿状态可确认）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long returnId) {
        SoReturn soReturn = baseMapper.selectById(returnId);
        if (soReturn == null) {
            throw new RuntimeException("退货单不存在");
        }
        if (!STATUS_DRAFT.equals(soReturn.getStatus())) {
            throw new RuntimeException("只有草稿状态的退货单才能确认");
        }
        soReturn.setStatus(STATUS_CONFIRMED);
        baseMapper.updateById(soReturn);
    }

    /**
     * 执行退货入库（扣减库存并生成库存交易记录）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long returnId) {
        SoReturn soReturn = baseMapper.selectById(returnId);
        if (soReturn == null) {
            throw new RuntimeException("退货单不存在");
        }
        if (STATUS_RECEIVED.equals(soReturn.getStatus())) {
            throw new RuntimeException("退货单已收货");
        }

        List<SoReturnLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(SoReturnLine.class)
                .eq(SoReturnLine::getReturnId, returnId));

        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("退货单无明细行");
        }

        for (SoReturnLine line : lines) {
            postReturnStock(soReturn, line, new Date());
        }

        soReturn.setStatus(STATUS_RECEIVED);
        baseMapper.updateById(soReturn);
        log.info("退货单执行完成, returnId={}", returnId);
    }

    // ==================== Flowable 工作流集成 ====================

    /**
     * 提交审批流程
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String returnIdStr, String selectFlowId) {
        Long returnId = Long.parseLong(returnIdStr);
        SoReturn entity = baseMapper.selectById(returnId);
        if (entity == null) {
            throw new RuntimeException("退货单不存在");
        }
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new RuntimeException("只有草稿状态的退货单才能提交审批");
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
        R<Void> result = processService.startProcessByDataId(returnIdStr, selectFlowId, "soReturnService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new RuntimeException(result.getMsg());
    }

    /**
     * 获取流程实例ID
     */
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

    /**
     * 流程审批后处理（更新退货单状态）
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterFlowHandle(WfMyBusiness business) {
        Long returnId = Long.parseLong(business.getDataId());
        SoReturn entity = baseMapper.selectById(returnId);
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

    /**
     * 根据数据ID获取业务数据
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Object getBusinessDataById(String dataId) {
        return selectById(Long.parseLong(dataId));
    }

    /**
     * 获取流程任务变量
     */
    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 获取流程任务候选用户
     */
    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    private void postReturnStock(SoReturn soReturn, SoReturnLine line, Date now) {
        if (line.getReturnQuantity() == null || line.getReturnQuantity().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("退货数量必须大于0");
        }
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        lqw.eq(soReturn.getWarehouseId() != null, WmMaterialStock::getWarehouseId, soReturn.getWarehouseId());
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        WmMaterialStock stock = stockMapper.selectOne(lqw);

        java.math.BigDecimal beforeQuantity = stock == null || stock.getStockQuantity() == null
            ? java.math.BigDecimal.ZERO : stock.getStockQuantity();
        java.math.BigDecimal afterQuantity = beforeQuantity.add(line.getReturnQuantity());

        if (stock == null) {
            stock = new WmMaterialStock();
            stock.setMaterialId(line.getMaterialId());
            stock.setMaterialCode(line.getMaterialCode());
            stock.setMaterialName(line.getMaterialName());
            stock.setMaterialSpec(line.getMaterialSpec());
            stock.setBaseUomId(line.getBaseUomId());
            stock.setBaseUomName(line.getBaseUomName());
            stock.setWarehouseId(soReturn.getWarehouseId());
            stock.setWarehouseCode(soReturn.getWarehouseCode());
            stock.setWarehouseName(soReturn.getWarehouseName());
            stock.setStockQuantity(afterQuantity);
            stock.setRecptDate(now);
            stock.setFrozenFlag(0L);
            stockMapper.insert(stock);
        } else {
            stock.setStockQuantity(afterQuantity);
            stock.setRecptDate(now);
            stockMapper.updateById(stock);
        }

        WmMaterialStockTx tx = new WmMaterialStockTx();
        tx.setTxType("RETURN_IN");
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
        tx.setTxFlag(1);
        tx.setTxQuantity(line.getReturnQuantity());
        tx.setBeforeQuantity(beforeQuantity);
        tx.setAfterQuantity(afterQuantity);
        tx.setTxDate(now);
        tx.setSourceDocType("SO_RETURN");
        tx.setSourceDocId(soReturn.getReturnId());
        tx.setSourceDocCode(soReturn.getReturnNo());
        tx.setSourceDocLineId(line.getLineId());
        tx.setRemark("销售退货入库，原订单号：" + soReturn.getSoNo());
        stockTxMapper.insert(tx);
    }

    private void insertOrUpdateLines(Long returnId, List<SoReturnLine> lineList) {
        if (lineList != null && !lineList.isEmpty()) {
            for (SoReturnLine line : lineList) {
                line.setLineId(null);
                line.setReturnId(returnId);
                lineMapper.insert(line);
            }
        }
    }

    private void deleteLinesByReturnId(Long returnId) {
        if (returnId != null) {
            LambdaQueryWrapper<SoReturnLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(SoReturnLine::getReturnId, returnId);
            lineMapper.delete(lqw);
        }
    }

    private LambdaQueryWrapper<SoReturn> buildQueryWrapper(SoReturnBo bo) {
        LambdaQueryWrapper<SoReturn> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getReturnNo()), SoReturn::getReturnNo, bo.getReturnNo());
        lqw.eq(StringUtils.isNotBlank(bo.getSoNo()), SoReturn::getSoNo, bo.getSoNo());
        lqw.eq(bo.getClientId() != null, SoReturn::getClientId, bo.getClientId());
        lqw.like(StringUtils.isNotBlank(bo.getClientName()), SoReturn::getClientName, bo.getClientName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SoReturn::getStatus, bo.getStatus());
        lqw.orderByDesc(SoReturn::getCreateTime);
        return lqw;
    }
}
