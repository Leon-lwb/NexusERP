package com.nexus.erp.pu.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.pu.domain.PuPurchaseReturn;
import com.nexus.erp.pu.domain.PuPurchaseReturnLine;
import com.nexus.erp.pu.domain.bo.PuPurchaseReturnBo;
import com.nexus.erp.pu.domain.vo.PuPurchaseReturnVo;
import com.nexus.erp.pu.invoice.service.IPuInvoiceService;
import com.nexus.erp.pu.mapper.PuPurchaseReturnLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseReturnMapper;
import com.nexus.erp.pu.service.IPuPurchaseReturnService;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
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
 * <p>
 * 实现采购退货单的核心业务逻辑，包括：
 * <ul>
 *   <li>CRUD 操作：分页查询、新增（自动编号/默认状态）、修改（状态校验）、删除</li>
 *   <li>状态流转：确认（草稿→已确认）、执行退货出库（扣减库存并生成库存交易记录）</li>
 *   <li>流程审批集成：提交审批、流程实例查询、审批状态回调处理</li>
 * </ul>
 *
 * @author nexus
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
    private final FlowableProcInsIdHelper procInsIdHelper;
    private final IPuInvoiceService puInvoiceService;

    /**
     * 分页查询采购退货单列表
     */
    @Override
    public TableDataInfo<PuPurchaseReturnVo> selectPageList(PuPurchaseReturnBo bo, PageQuery pageQuery) {
        Page<PuPurchaseReturnVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        return TableDataInfo.build(page);
    }

    /**
     * 查询采购退货单列表（不分页）
     */
    @Override
    public List<PuPurchaseReturnVo> selectList(PuPurchaseReturnBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据主键查询采购退货单，附带明细行列表
     */
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

    /**
     * 新增采购退货单
     * <p>
     * 自动生成退货单号（如为空）、默认设为草稿状态、默认退货日期为当天，同时保存明细行。
     */
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

    /**
     * 修改采购退货单
     * <p>
     * 仅草稿状态的退货单允许编辑。修改时先删除全部旧明细行，再插入新明细行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PuPurchaseReturnBo bo) {
        PuPurchaseReturn entity = MapstructUtils.convert(bo, PuPurchaseReturn.class);
        PuPurchaseReturn current = baseMapper.selectById(entity.getReturnId());
        if (current == null) {
            throw new ServiceException("采购退货单不存在");
        }
        if (!STATUS_DRAFT.equals(current.getStatus())) {
            throw new ServiceException("只有草稿状态的采购退货单才能编辑");
        }
        deleteLines(entity.getReturnId());
        insertLines(entity.getReturnId(), bo.getPuPurchaseReturnLineList());
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除采购退货单
     * <p>
     * 先删除各退货单的明细行，再删除主表记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteLines(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 确认退货单：仅草稿状态允许确认，将状态改为已确认
     *
     * @param returnId 采购退货单 ID
     */
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

    /**
     * 执行退货出库：扣减库存并生成库存交易记录
     * <p>
     * 遍历退货单明细行，逐行扣减对应物料库存数量，并生成 WM_MATERIAL_STOCK_TX 库存交易记录。
     * 仅已确认状态允许执行，已执行状态直接返回。
     *
     * @param returnId 采购退货单 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long returnId) {
        PuPurchaseReturn purchaseReturn = ensureReturn(returnId);
        if (STATUS_EXECUTED.equals(purchaseReturn.getStatus())) {
            return;
        }
        List<PuPurchaseReturnLine> lines = lineMapper.selectList(Wrappers.lambdaQuery(PuPurchaseReturnLine.class).eq(PuPurchaseReturnLine::getReturnId, returnId));
        if (lines == null || lines.isEmpty()) {
            throw new ServiceException("采购退货单无明细行");
        }
        for (PuPurchaseReturnLine line : lines) {
            postReturnOut(purchaseReturn, line, new Date());
        }
        // 退货进项转出:查找覆盖退货 PO 行的已抵扣蓝字进项发票,部分红冲(仅 cert_status=DEDUCTED 才转出)
        Map<Long, BigDecimal> poLineReturnQty = new HashMap<>();
        for (PuPurchaseReturnLine line : lines) {
            if (line.getOrderLineId() != null && line.getReturnQuantity() != null
                && line.getReturnQuantity().compareTo(BigDecimal.ZERO) > 0) {
                poLineReturnQty.merge(line.getOrderLineId(), line.getReturnQuantity(), BigDecimal::add);
            }
        }
        if (!poLineReturnQty.isEmpty()) {
            int redCount = puInvoiceService.redFlushForReturnByPoLines(poLineReturnQty);
            if (redCount > 0) {
                log.info("采购退货[{}]触发进项转出红冲,生成红字发票{}张", purchaseReturn.getReturnNo(), redCount);
            }
        }
        purchaseReturn.setStatus(STATUS_EXECUTED);
        baseMapper.updateById(purchaseReturn);
    }

    // ==================== Flowable 工作流集成 ====================

    /**
     * 提交采购退货单审批（启动 Flowable 审批流）
     *
     * @param returnIdStr    退货单ID字符串
     * @param selectFlowId   选择的审批流ID
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String returnIdStr, String selectFlowId) {
        Long returnId = Long.parseLong(returnIdStr);
        PuPurchaseReturn entity = baseMapper.selectById(returnId);
        if (entity == null) {
            throw new ServiceException("采购退货单不存在");
        }
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new ServiceException("只有草稿状态的采购退货单才能提交审批");
        }
        if (StringUtils.isBlank(selectFlowId)) {
            if (entity.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(entity.getSelectFlowId());
            } else {
                throw new ServiceException("请先在编辑页选择审批流程并保存");
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

    /**
     * 获取采购退货单流程实例ID
     *
     * @param returnIdStr 退货单ID字符串
     * @return 流程实例ID
     */
    @Override
    public String getProcInsId(String returnIdStr) {
        return procInsIdHelper.resolve(returnIdStr, () -> {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(returnIdStr);
            return business != null ? business.getProcessInstanceId() : null;
        });
    }

    /**
     * 流程审批状态变更回调
     * <p>
     * 根据 Flowable 流程审批结果更新退货单状态：
     * <ul>
     *   <li>审批中 → APPROVING</li>
     *   <li>通过 → CONFIRMED</li>
     *   <li>驳回/退回 → DRAFT</li>
     *   <li>撤销/停止/召回 → DRAFT</li>
     * </ul>
     *
     * @param business 流程业务对象
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
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

    /**
     * 根据业务数据ID获取采购退货单详情（供流程引擎调用）
     *
     * @param dataId 业务数据ID
     * @return 采购退货单视图对象
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Object getBusinessDataById(String dataId) {
        return selectById(Long.parseLong(dataId));
    }

    /**
     * 获取流程任务变量值（供流程引擎调用）
     *
     * @param taskNameId 任务节点标识
     * @param values     当前变量值
     * @return 任务变量值映射
     */
    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 获取流程任务候选用户列表（供流程引擎调用）
     *
     * @param taskNameId 任务节点标识
     * @param values     当前变量值
     * @return 候选用户名列表
     */
    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 执行退货出库并生成库存交易记录
     * <p>
     * 扣减对应物料库存数量，并生成 WM_MATERIAL_STOCK_TX 库存交易记录用于追溯。
     * 校验库存充足性，库存不足时抛出异常。
     *
     * @param purchaseReturn 采购退货单实体
     * @param line           退货明细行
     * @param now            当前时间
     */
    private void postReturnOut(PuPurchaseReturn purchaseReturn, PuPurchaseReturnLine line, Date now) {
        if (line.getReturnQuantity() == null || line.getReturnQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("退货数量必须大于0");
        }
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        WmMaterialStock stock = stockMapper.selectOne(lqw);
        if (stock == null || stock.getStockQuantity() == null || stock.getStockQuantity().compareTo(line.getReturnQuantity()) < 0) {
            throw new ServiceException("库存不足，无法执行采购退货");
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

    /**
     * 批量插入采购退货明细行
     *
     * @param returnId 采购退货单ID
     * @param lineList 明细行列表
     */
    private void insertLines(Long returnId, List<PuPurchaseReturnLine> lineList) {
        if (returnId == null || lineList == null || lineList.isEmpty()) {
            return;
        }
        for (PuPurchaseReturnLine line : lineList) {
            line.setLineId(null);
            line.setReturnId(returnId);
            lineMapper.insert(line);
        }
    }

    /**
     * 删除指定采购退货单的所有明细行
     *
     * @param returnId 采购退货单ID
     */
    private void deleteLines(Long returnId) {
        if (returnId == null) {
            return;
        }
        lineMapper.delete(Wrappers.lambdaQuery(PuPurchaseReturnLine.class).eq(PuPurchaseReturnLine::getReturnId, returnId));
    }

    /**
     * 确保采购退货单存在，否则抛出异常
     *
     * @param returnId 采购退货单ID
     * @return 采购退货单实体
     */
    private PuPurchaseReturn ensureReturn(Long returnId) {
        PuPurchaseReturn purchaseReturn = baseMapper.selectById(returnId);
        if (purchaseReturn == null) {
            throw new ServiceException("采购退货单不存在");
        }
        return purchaseReturn;
    }

    /**
     * 构建查询条件 Wrapper
     * <p>
     * 支持按退货单号精确匹配、按采购订单号精确匹配、按状态精确匹配、按供应商名称模糊查询，默认按创建时间降序排列。
     *
     * @param bo 查询条件
     * @return 查询条件 Wrapper
     */
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
