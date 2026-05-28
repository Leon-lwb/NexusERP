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
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.pu.domain.PuPurchaseRequisition;
import com.nexus.erp.pu.domain.PuPurchaseRequisitionLine;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;
import com.nexus.erp.pu.domain.bo.PuPurchaseRequisitionBo;
import com.nexus.erp.pu.domain.vo.PuPurchaseRequisitionVo;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseOrderMapper;
import com.nexus.erp.pu.mapper.PuPurchaseRequisitionLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseRequisitionMapper;
import com.nexus.erp.pu.service.IPuPurchaseRequisitionService;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购申请服务实现类
 * <p>
 * 实现采购申请的核心业务逻辑，包括：
 * <ul>
 *   <li>CRUD 操作：分页查询、新增（自动编号/默认状态）、修改（状态校验）、删除</li>
 *   <li>状态流转：提交（草稿→已提交）、审批（已提交→已批准）、关闭、作废</li>
 *   <li>转单操作：将已批准的采购申请转换为采购订单</li>
 * </ul>
 *
 * @author ruoyi
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("puPurchaseRequisitionService")
public class PuPurchaseRequisitionServiceImpl implements IPuPurchaseRequisitionService {

    private final PuPurchaseRequisitionMapper baseMapper;
    private final PuPurchaseRequisitionLineMapper lineMapper;
    private final PuPurchaseOrderMapper purchaseOrderMapper;
    private final PuPurchaseOrderLineMapper purchaseOrderLineMapper;
    private final BdMaterialMapper bdMaterialMapper;
    private final WmMaterialStockMapper materialStockMapper;
    private final AutoCodeUtil autoCodeUtil;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    /**
     * 分页查询采购申请列表
     */
    @Override
    public TableDataInfo<PuPurchaseRequisitionVo> selectPageList(PuPurchaseRequisitionBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PuPurchaseRequisition> lqw = buildQueryWrapper(bo);
        Page<PuPurchaseRequisitionVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询采购申请列表（不分页）
     */
    @Override
    public List<PuPurchaseRequisitionVo> selectList(PuPurchaseRequisitionBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据主键查询采购申请，附带明细行列表
     */
    @Override
    public PuPurchaseRequisitionVo selectById(Long id) {
        PuPurchaseRequisitionVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getRequisitionId() != null) {
            LambdaQueryWrapper<PuPurchaseRequisitionLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(PuPurchaseRequisitionLine::getRequisitionId, vo.getRequisitionId());
            vo.setPuPurchaseRequisitionLineList(lineMapper.selectList(lqw));
        }
        return vo;
    }

    /**
     * 新增采购申请
     * <p>
     * 自动生成申请单号（如为空）、默认设为草稿状态、默认申请日期为当天，同时保存明细行。
     * 如果 BO 中已存在 ID，则自动转为更新操作，防止前端误调用导致重复插入。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PuPurchaseRequisitionBo bo) {
        if (bo.getRequisitionId() != null) {
            return updateByBo(bo);
        }
        PuPurchaseRequisition entity = MapstructUtils.convert(bo, PuPurchaseRequisition.class);
        if (StringUtils.isBlank(entity.getRequisitionNo())) {
            entity.setRequisitionNo(autoCodeUtil.genSerialCode("PU_REQUISITION_CODE", null));
        }
        if (entity.getStatus() == null) {
            entity.setStatus(STATUS_DRAFT);
        }
        if (entity.getRequisitionDate() == null) {
            entity.setRequisitionDate(new Date());
        }
        int rows = baseMapper.insert(entity);
        insertLines(entity.getRequisitionId(), bo.getPuPurchaseRequisitionLineList());
        return rows;
    }

    /**
     * 修改采购申请
     * <p>
     * 仅草稿或已提交状态的申请允许编辑。修改时先删除全部旧明细行，再插入新明细行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PuPurchaseRequisitionBo bo) {
        PuPurchaseRequisition entity = MapstructUtils.convert(bo, PuPurchaseRequisition.class);
        PuPurchaseRequisition current = baseMapper.selectById(entity.getRequisitionId());
        if (current == null) {
            throw new ServiceException("采购申请单不存在");
        }
        if (!STATUS_DRAFT.equals(current.getStatus()) && !STATUS_SUBMITTED.equals(current.getStatus())) {
            throw new ServiceException("当前状态不允许编辑采购申请单");
        }
        deleteLinesByRequisitionId(entity.getRequisitionId());
        insertLines(entity.getRequisitionId(), bo.getPuPurchaseRequisitionLineList());
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除采购申请，同时删除关联的明细行
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteLinesByRequisitionId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 提交采购申请：仅草稿状态允许提交，将状态改为已提交
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long requisitionId) {
        PuPurchaseRequisition requisition = ensureRequisition(requisitionId);
        if (!STATUS_DRAFT.equals(requisition.getStatus())) {
            throw new ServiceException("只有草稿状态的采购申请才能提交");
        }
        requisition.setStatus(STATUS_SUBMITTED);
        baseMapper.updateById(requisition);
    }

    // ==================== Flowable 工作流集成 ====================

    /**
     * 提交流程审批（启动 Flowable 审批流）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String requisitionIdStr, String selectFlowId) {
        Long requisitionId = Long.parseLong(requisitionIdStr);
        PuPurchaseRequisition requisition = baseMapper.selectById(requisitionId);
        if (requisition == null) {
            throw new ServiceException("采购申请单不存在");
        }

        // 如果前端没传 selectFlowId，从数据库已保存记录中读取
        if (StringUtils.isBlank(selectFlowId)) {
            if (requisition.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(requisition.getSelectFlowId());
            } else {
                throw new ServiceException("请先在编辑页选择审批流程并保存");
            }
        }

        // 发起 Flowable 流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", requisitionIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("requisitionId", requisition.getRequisitionId());
        variables.put("requisitionNo", requisition.getRequisitionNo());
        variables.put("requisitionName", requisition.getRequisitionName());
        variables.put("applicantName", requisition.getApplicantName());
        variables.put("requisitionDate", requisition.getRequisitionDate());
        R<Void> result = processService.startProcessByDataId(requisitionIdStr, selectFlowId, "puPurchaseRequisitionService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new ServiceException(result.getMsg());
    }

    @Override
    public String getProcInsId(String requisitionIdStr) {
        // 第一级：查 wf_my_business 表
        try {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(requisitionIdStr);
            if (business != null && StringUtils.isNotBlank(business.getProcessInstanceId())) {
                return business.getProcessInstanceId();
            }
        } catch (Exception ignored) {
        }

        // 第二级：查运行中的流程实例
        ProcessInstance runtime = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(requisitionIdStr).singleResult();
        if (runtime != null) {
            return runtime.getId();
        }

        // 第三级：查历史流程实例
        HistoricProcessInstance hist = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(requisitionIdStr).singleResult();
        return hist != null ? hist.getId() : null;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterFlowHandle(WfMyBusiness business) {
        Long requisitionId = Long.parseLong(business.getDataId());
        PuPurchaseRequisition requisition = baseMapper.selectById(requisitionId);
        if (requisition == null) {
            return;
        }
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            requisition.setStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            requisition.setStatus(STATUS_APPROVED);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            requisition.setStatus(STATUS_REJECTED);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            requisition.setStatus(STATUS_DRAFT);
        }
        baseMapper.updateById(requisition);
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

    // ==================== 原有审批流（保留兼容） ====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long requisitionId) {
        PuPurchaseRequisition requisition = ensureRequisition(requisitionId);
        if (!STATUS_SUBMITTED.equals(requisition.getStatus()) && !STATUS_APPROVING.equals(requisition.getStatus())) {
            throw new ServiceException("当前状态不允许审批采购申请");
        }
        requisition.setStatus(STATUS_APPROVED);
        baseMapper.updateById(requisition);
    }

    /**
     * 关闭采购申请：直接设为已关闭状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long requisitionId) {
        PuPurchaseRequisition requisition = ensureRequisition(requisitionId);
        requisition.setStatus(STATUS_CLOSED);
        baseMapper.updateById(requisition);
    }

    /**
     * 作废采购申请：已转单的申请不允许作废
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long requisitionId) {
        PuPurchaseRequisition requisition = ensureRequisition(requisitionId);
        if (STATUS_ORDERED.equals(requisition.getStatus())) {
            throw new ServiceException("已转单的采购申请不能作废");
        }
        requisition.setStatus(STATUS_CANCELLED);
        baseMapper.updateById(requisition);
    }

    /**
     * 将已批准的采购申请转换为采购订单
     * <p>
     * 校验采购申请已批准且有明细行，然后创建采购订单及其明细行，
     * 并将采购申请状态更新为"已转单"（ORDERED）。
     *
     * @param requisitionId 采购申请ID
     * @return 生成的采购订单ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long convertToPurchaseOrder(Long requisitionId) {
        PuPurchaseRequisition requisition = ensureRequisition(requisitionId);
        if (!STATUS_APPROVED.equals(requisition.getStatus())) {
            throw new ServiceException("只有已批准的采购申请才能转采购订单");
        }
        List<PuPurchaseRequisitionLine> lines = lineMapper.selectList(Wrappers.lambdaQuery(PuPurchaseRequisitionLine.class).eq(PuPurchaseRequisitionLine::getRequisitionId, requisitionId));
        if (lines == null || lines.isEmpty()) {
            throw new ServiceException("采购申请无明细行");
        }

        // 创建采购订单主表
        PuPurchaseOrder order = new PuPurchaseOrder();
        order.setOrderNo(autoCodeUtil.genSerialCode("PU_ORDER_CODE", null));
        order.setRequisitionId(requisition.getRequisitionId());
        order.setRequisitionNo(requisition.getRequisitionNo());
        order.setOrderDate(new Date());
        order.setStatus("DRAFT");
        order.setRemark("由采购申请【" + requisition.getRequisitionNo() + "】转单生成");
        purchaseOrderMapper.insert(order);

        // 逐行转换明细：将采购申请明细行的物料信息拷贝到采购订单明细行
        for (PuPurchaseRequisitionLine reqLine : lines) {
            PuPurchaseOrderLine orderLine = new PuPurchaseOrderLine();
            orderLine.setOrderId(order.getOrderId());
            orderLine.setRequisitionLineId(reqLine.getLineId());
            orderLine.setMaterialId(reqLine.getMaterialId());
            orderLine.setMaterialCode(reqLine.getMaterialCode());
            orderLine.setMaterialName(reqLine.getMaterialName());
            orderLine.setMaterialSpec(reqLine.getMaterialSpec());
            orderLine.setUnit(reqLine.getUnit());
            orderLine.setOrderQuantity(reqLine.getApplyQuantity());
            orderLine.setReceivedQuantity(java.math.BigDecimal.ZERO);
            orderLine.setSortOrder(reqLine.getAttr3() != null ? reqLine.getAttr3().intValue() : null);
            orderLine.setRemark(reqLine.getRemark());
            purchaseOrderLineMapper.insert(orderLine);
        }

        // 更新采购申请状态为"已转单"
        requisition.setStatus(STATUS_ORDERED);
        baseMapper.updateById(requisition);
        log.info("采购申请 {} 已转采购订单 {}", requisitionId, order.getOrderId());
        return order.getOrderId();
    }

    /**
     * 批量插入采购申请明细行
     *
     * @param requisitionId 采购申请ID
     * @param lineList      明细行列表
     */
    private void insertLines(Long requisitionId, List<PuPurchaseRequisitionLine> lineList) {
        if (requisitionId == null || lineList == null || lineList.isEmpty()) {
            return;
        }
        for (PuPurchaseRequisitionLine line : lineList) {
            line.setLineId(null);
            line.setRequisitionId(requisitionId);
            lineMapper.insert(line);
        }
    }

    /**
     * 删除指定采购申请的所有明细行
     *
     * @param requisitionId 采购申请ID
     */
    private void deleteLinesByRequisitionId(Long requisitionId) {
        if (requisitionId == null) {
            return;
        }
        lineMapper.delete(Wrappers.lambdaQuery(PuPurchaseRequisitionLine.class).eq(PuPurchaseRequisitionLine::getRequisitionId, requisitionId));
    }

    /**
     * 确保采购申请存在，否则抛出异常
     *
     * @return 采购申请实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> generateFromLowStock() {
        List<BdMaterial> materials = bdMaterialMapper.selectList(
            new LambdaQueryWrapper<BdMaterial>()
                .eq(BdMaterial::getStatus, "ACTIVE")
                .isNotNull(BdMaterial::getSafetyStock)
                .gt(BdMaterial::getSafetyStock, BigDecimal.ZERO));

        if (materials == null || materials.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, BigDecimal> stockMap = new HashMap<>();
        List<WmMaterialStock> stocks = materialStockMapper.selectList(new LambdaQueryWrapper<>());
        if (stocks != null) {
            for (WmMaterialStock stock : stocks) {
                BigDecimal current = stockMap.getOrDefault(stock.getMaterialId(), BigDecimal.ZERO);
                stockMap.put(stock.getMaterialId(), current.add(stock.getStockQuantity() != null ? stock.getStockQuantity() : BigDecimal.ZERO));
            }
        }

        Map<String, Long> existingRequisitionMap = new HashMap<>();
        List<PuPurchaseRequisition> existingRequisitions = baseMapper.selectList(
            new LambdaQueryWrapper<PuPurchaseRequisition>()
                .in(PuPurchaseRequisition::getStatus, "DRAFT", "SUBMITTED", "APPROVED")
                .eq(PuPurchaseRequisition::getSourceType, "LOW_STOCK"));
        if (existingRequisitions != null) {
            for (PuPurchaseRequisition req : existingRequisitions) {
                existingRequisitionMap.put(req.getRequisitionNo(), req.getRequisitionId());
            }
        }

        List<Long> generatedIds = new ArrayList<>();
        List<PuPurchaseRequisitionLine> lowStockLines = new ArrayList<>();

        for (BdMaterial material : materials) {
            BigDecimal currentStock = stockMap.getOrDefault(material.getMaterialId(), BigDecimal.ZERO);
            BigDecimal safetyStock = material.getSafetyStock();

            if (currentStock.compareTo(safetyStock) < 0) {
                BigDecimal maxStock = material.getMaxStock() != null ? material.getMaxStock() : safetyStock.multiply(new BigDecimal("2"));
                BigDecimal suggestQty = maxStock.subtract(currentStock);

                if (suggestQty.compareTo(BigDecimal.ZERO) > 0) {
                    PuPurchaseRequisitionLine line = new PuPurchaseRequisitionLine();
                    line.setMaterialId(material.getMaterialId());
                    line.setMaterialCode(material.getMaterialCode());
                    line.setMaterialName(material.getMaterialName());
                    line.setMaterialSpec(material.getMaterialSpec());
                    line.setApplyQuantity(suggestQty);
                    line.setSourceType("LOW_STOCK");
                    line.setSourceId(material.getMaterialId());
                    lowStockLines.add(line);
                }
            }
        }

        if (lowStockLines.isEmpty()) {
            return new ArrayList<>();
        }

        String reqCode = autoCodeUtil.genSerialCode("pu_purchase_requisition", "LOW");
        PuPurchaseRequisition requisition = new PuPurchaseRequisition();
        requisition.setRequisitionNo(reqCode);
        requisition.setSourceType("LOW_STOCK");
        requisition.setRequisitionDate(new Date());
        requisition.setStatus("DRAFT");
        baseMapper.insert(requisition);

        for (int i = 0; i < lowStockLines.size(); i++) {
            PuPurchaseRequisitionLine line = lowStockLines.get(i);
            line.setRequisitionId(requisition.getRequisitionId());
            lineMapper.insert(line);
        }

        generatedIds.add(requisition.getRequisitionId());
        return generatedIds;
    }

    private PuPurchaseRequisition ensureRequisition(Long requisitionId) {
        PuPurchaseRequisition requisition = baseMapper.selectById(requisitionId);
        if (requisition == null) {
            throw new ServiceException("采购申请单不存在");
        }
        return requisition;
    }

    /**
     * 构建查询条件 Wrapper
     * <p>
     * 支持按申请单号精确匹配、按申请名称模糊匹配、按状态精确匹配，默认按创建时间降序排列。
     *
     * @param bo 查询条件
     * @return 查询条件 Wrapper
     */
    private LambdaQueryWrapper<PuPurchaseRequisition> buildQueryWrapper(PuPurchaseRequisitionBo bo) {
        LambdaQueryWrapper<PuPurchaseRequisition> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getRequisitionNo()), PuPurchaseRequisition::getRequisitionNo, bo.getRequisitionNo());
        lqw.like(StringUtils.isNotBlank(bo.getRequisitionName()), PuPurchaseRequisition::getRequisitionName, bo.getRequisitionName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), PuPurchaseRequisition::getStatus, bo.getStatus());
        lqw.orderByDesc(PuPurchaseRequisition::getCreateTime);
        return lqw;
    }
}
