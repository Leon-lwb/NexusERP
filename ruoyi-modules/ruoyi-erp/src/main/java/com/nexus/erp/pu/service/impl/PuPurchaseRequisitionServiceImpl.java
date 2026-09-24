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
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import org.springframework.beans.BeanUtils;
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
import java.util.stream.Collectors;

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
 * @author nexus
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
    private final FlowableProcInsIdHelper procInsIdHelper;

    /**
     * 分页查询采购申请列表
     */
    @Override
    public TableDataInfo<PuPurchaseRequisitionVo> selectPageList(PuPurchaseRequisitionBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PuPurchaseRequisition> lqw = buildQueryWrapper(bo);
        Page<PuPurchaseRequisition> page = baseMapper.selectPage(pageQuery.build(), lqw);
        List<PuPurchaseRequisitionVo> records = page.getRecords().stream().map(this::copyToVo).collect(Collectors.toList());
        Page<PuPurchaseRequisitionVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(records);
        return TableDataInfo.build(voPage);
    }

    /**
     * 查询采购申请列表（不分页）
     */
    @Override
    public List<PuPurchaseRequisitionVo> selectList(PuPurchaseRequisitionBo bo) {
        List<PuPurchaseRequisition> list = baseMapper.selectList(buildQueryWrapper(bo));
        return list.stream().map(this::copyToVo).collect(Collectors.toList());
    }

    /**
     * 根据主键查询采购申请，附带明细行列表
     */
    @Override
    public PuPurchaseRequisitionVo selectById(Long id) {
        PuPurchaseRequisition entity = baseMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        PuPurchaseRequisitionVo vo = copyToVo(entity);
        LambdaQueryWrapper<PuPurchaseRequisitionLine> lqw = Wrappers.lambdaQuery();
        lqw.eq(PuPurchaseRequisitionLine::getRequisitionId, vo.getRequisitionId());
        vo.setPuPurchaseRequisitionLineList(lineMapper.selectList(lqw));
        return vo;
    }

    /**
     * 将实体转换为 VO 对象
     */
    private PuPurchaseRequisitionVo copyToVo(PuPurchaseRequisition entity) {
        if (entity == null) {
            return null;
        }
        PuPurchaseRequisitionVo vo = new PuPurchaseRequisitionVo();
        BeanUtils.copyProperties(entity, vo);
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
        if (bo != null && bo.getRequisitionId() != null) {
            return updateByBo(bo);
        }
        return createDraftInternal(bo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDraft(PuPurchaseRequisitionBo bo) {
        createDraftInternal(bo);
        return bo.getRequisitionId();
    }

    private int createDraftInternal(PuPurchaseRequisitionBo bo) {
        if (bo == null) {
            throw new IllegalArgumentException("采购申请不能为空");
        }
        if (bo.getRequisitionId() != null) {
            throw new ServiceException("新建采购申请时ID必须为空");
        }
        List<PuPurchaseRequisitionLine> lines = bo.getPuPurchaseRequisitionLineList();
        if (lines == null || lines.isEmpty() || lines.stream().anyMatch(line ->
            line == null || line.getMaterialId() == null || line.getMaterialId() <= 0
                || line.getApplyQuantity() == null || line.getApplyQuantity().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new ServiceException("采购申请至少需要一条有效明细");
        }

        PuPurchaseRequisition entity = MapstructUtils.convert(bo, PuPurchaseRequisition.class);
        if (StringUtils.isBlank(entity.getRequisitionNo())) {
            entity.setRequisitionNo(autoCodeUtil.genSerialCode("PU_REQUISITION_CODE", null));
        }
        if (entity.getRequisitionDate() == null) {
            entity.setRequisitionDate(new Date());
        }
        entity.setStatus(STATUS_DRAFT);

        int rows = baseMapper.insert(entity);
        if (rows != 1) {
            throw new ServiceException("采购申请主表写入失败");
        }
        if (entity.getRequisitionId() == null || entity.getRequisitionId() <= 0
            || StringUtils.isBlank(entity.getRequisitionNo())) {
            throw new ServiceException("采购申请主表未生成有效ID或编号");
        }

        bo.setRequisitionId(entity.getRequisitionId());
        bo.setRequisitionNo(entity.getRequisitionNo());
        bo.setStatus(entity.getStatus());
        bo.setCreateTime(entity.getCreateTime());
        bo.setUpdateTime(entity.getUpdateTime());
        insertLines(entity.getRequisitionId(), lines);
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
        // ★ 状态只能由 submit/afterFlowHandle/close 等状态机入口变更(ERP-PU-04)
        entity.setStatus(null);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDraftIfUnchanged(Long requisitionId, String versionMarker) {
        VersionMarker marker = parseVersionMarker(requisitionId, versionMarker);
        if (marker == null) {
            return false;
        }
        LambdaQueryWrapper<PuPurchaseRequisition> wrapper = Wrappers.lambdaQuery(PuPurchaseRequisition.class)
            .eq(PuPurchaseRequisition::getRequisitionId, requisitionId)
            .eq(PuPurchaseRequisition::getStatus, STATUS_DRAFT);
        if (marker.type() == 'U') {
            wrapper.eq(PuPurchaseRequisition::getUpdateTime, marker.time());
        } else {
            wrapper.isNull(PuPurchaseRequisition::getUpdateTime)
                .eq(PuPurchaseRequisition::getCreateTime, marker.time());
        }
        if (baseMapper.delete(wrapper) != 1) {
            return false;
        }
        deleteLinesByRequisitionId(requisitionId);
        return true;
    }

    // /**
    //  * 提交采购申请（已废弃，请使用 Flowable 工作流提交审批）
    //  */
    // @Override
    // @Transactional(rollbackFor = Exception.class)
    // public void submit(Long requisitionId) {
    //     PuPurchaseRequisition requisition = ensureRequisition(requisitionId);
    //     if (!STATUS_DRAFT.equals(requisition.getStatus())) {
    //         throw new ServiceException("只有草稿状态的采购申请才能提交");
    //     }
    //     requisition.setStatus(STATUS_SUBMITTED);
    //     baseMapper.updateById(requisition);
    // }

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

    /**
     * 获取采购申请对应的流程实例ID（运行时优先，其次历史）。
     *
     * @param requisitionIdStr 采购申请ID（字符串）
     * @return 流程实例ID，无则返回 null
     */
    @Override
    public String getProcInsId(String requisitionIdStr) {
        return procInsIdHelper.resolve(requisitionIdStr, () -> {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(requisitionIdStr);
            return business != null ? business.getProcessInstanceId() : null;
        });
    }

    /**
     * 审批流节点处理完成后的回调：根据审批动作更新采购申请状态。
     *
     * @param business 工作流业务对象（含审批状态与数据ID）
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
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

    /**
     * 根据数据ID返回采购申请详情（供审批详情展示）。
     *
     * @param dataId 业务数据ID
     * @return 采购申请业务对象
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Object getBusinessDataById(String dataId) {
        return selectById(Long.parseLong(dataId));
    }

    /**
     * 获取审批任务所需的表单变量（当前实现无需额外变量，返回 null）。
     *
     * @param taskNameId 任务节点ID
     * @param values     已有流程变量
     * @return 任务表单变量，未实现返回 null
     */
    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 获取审批任务的候选处理人（当前实现未指定，返回 null）。
     *
     * @param taskNameId 任务节点ID
     * @param values     流程变量
     * @return 候选用户名列表，未实现返回 null
     */
    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    // /**
    //  * 审批采购申请（已废弃，请使用 Flowable 工作流审批）
    //  */
    // @Override
    // @Transactional(rollbackFor = Exception.class)
    // public void approve(Long requisitionId) {
    //     PuPurchaseRequisition requisition = ensureRequisition(requisitionId);
    //     if (!STATUS_SUBMITTED.equals(requisition.getStatus()) && !STATUS_APPROVING.equals(requisition.getStatus())) {
    //         throw new ServiceException("当前状态不允许审批采购申请");
    //     }
    //     requisition.setStatus(STATUS_APPROVED);
    //     baseMapper.updateById(requisition);
    // }

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

    private VersionMarker parseVersionMarker(Long id, String marker) {
        if (id == null || id <= 0 || marker == null || marker.length() < 3 || marker.charAt(1) != ':') {
            return null;
        }
        char type = marker.charAt(0);
        if (type != 'U' && type != 'C') {
            return null;
        }
        String millisText = marker.substring(2);
        if (millisText.isEmpty() || !millisText.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            long millis = Long.parseLong(millisText);
            return millis < 0 ? null : new VersionMarker(type, new Date(millis));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record VersionMarker(char type, Date time) {
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
                // ★ ERP-BD-02: bd_material.status 是 tinyint(0=停用/1=启用)，
                // 不能与供应商状态字符串 'ACTIVE' 比较（MySQL 隐式转换为 0，只会命中停用物料）。
                // 与 WmStockAlertServiceImpl:154 保持一致，用数值比较。
                .eq(BdMaterial::getStatus, 1L)
                .isNotNull(BdMaterial::getSafetyStock)
                .gt(BdMaterial::getSafetyStock, BigDecimal.ZERO));

        if (materials == null || materials.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, BigDecimal> stockMap = new HashMap<>();
        List<Long> materialIds = materials.stream()
            .map(BdMaterial::getMaterialId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        if (!materialIds.isEmpty()) {
            List<WmMaterialStock> stocks = materialStockMapper.selectList(
                new LambdaQueryWrapper<WmMaterialStock>()
                    .in(WmMaterialStock::getMaterialId, materialIds)
                    .eq(WmMaterialStock::getFrozenFlag, 0L));
            if (stocks != null) {
                for (WmMaterialStock stock : stocks) {
                    BigDecimal current = stockMap.getOrDefault(stock.getMaterialId(), BigDecimal.ZERO);
                    stockMap.put(stock.getMaterialId(), current.add(stock.getStockQuantity() != null ? stock.getStockQuantity() : BigDecimal.ZERO));
                }
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
        // 已有未完结低库存申请单的物料集合（用于去重：同一物料已生成申请则不再重复生成）
        java.util.Set<Long> alreadyRequestedMaterialIds = new java.util.HashSet<>();
        if (!existingRequisitionMap.isEmpty()) {
            List<PuPurchaseRequisitionLine> existingLines = lineMapper.selectList(
                new LambdaQueryWrapper<PuPurchaseRequisitionLine>()
                    .in(PuPurchaseRequisitionLine::getRequisitionId, existingRequisitionMap.values()));
            if (existingLines != null) {
                for (PuPurchaseRequisitionLine line : existingLines) {
                    if (line.getMaterialId() != null) {
                        alreadyRequestedMaterialIds.add(line.getMaterialId());
                    }
                }
            }
        }

        List<Long> generatedIds = new ArrayList<>();
        List<PuPurchaseRequisitionLine> lowStockLines = new ArrayList<>();

        for (BdMaterial material : materials) {
            // 去重：该物料已有未完结的低库存申请单则跳过
            if (alreadyRequestedMaterialIds.contains(material.getMaterialId())) {
                continue;
            }
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

        // 修复：原为 "pu_purchase_requisition"（小写），库中不存在该规则编码，
        // 会抛「未获取到指定类型:[pu_purchase_requisition]的业务编码生成规则」。
        // 与 L147 保持一致，复用 PU_REQUISITION_CODE 规则。
        String reqCode = autoCodeUtil.genSerialCode("PU_REQUISITION_CODE", null);
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

    /**
     * 加载采购申请单，不存在则抛异常。
     *
     * @param requisitionId 采购申请单ID
     * @return 采购申请单实体
     */
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
        LambdaQueryWrapper<PuPurchaseRequisition> lqw = Wrappers.<PuPurchaseRequisition>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getRequisitionNo()), PuPurchaseRequisition::getRequisitionNo, bo.getRequisitionNo())
            .like(StringUtils.isNotBlank(bo.getRequisitionName()), PuPurchaseRequisition::getRequisitionName, bo.getRequisitionName())
            .eq(bo.getDeptId() != null, PuPurchaseRequisition::getDeptId, bo.getDeptId())
            .eq(StringUtils.isNotBlank(bo.getStatus()), PuPurchaseRequisition::getStatus, bo.getStatus())
            .orderByDesc(PuPurchaseRequisition::getCreateTime);
        return lqw;
    }
}
