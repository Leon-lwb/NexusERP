package com.nexus.erp.prd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.bd.domain.vo.IndentedBomLineVo;
import com.nexus.erp.bd.service.IBdBomService;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdWorkOrderBo;
import com.nexus.erp.prd.domain.enums.WoStatusEnum;
import com.nexus.erp.prd.domain.vo.PrdWorkOrderVo;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.service.IPrdWorkOrderService;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.mapper.WmProductionIssueLineMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueMapper;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.erp.mq.message.ProductionCompleteMessage;
import com.nexus.common.mq.message.WorkOrderReleaseMessage;
import com.nexus.erp.wm.service.IWmStockOperationService;
import com.nexus.erp.wm.service.IWmStockReservationService;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产工单 Service 业务层实现
 * <p>
 * 实现生产工单相关的业务逻辑，包括工单的查询、新增、修改、删除、状态流转及工作流审批等。
 *
 * @author nexus
 */
@Slf4j
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
    private final ReliableMessageProducer messageProducer;
    private final AutoCodeUtil autoCodeUtil;
    private final IBdBomService bomService;
    private final IWmStockReservationService stockReservationService;

    /**
     * 分页查询生产工单列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 生产工单分页列表
     */
    @Override
    public TableDataInfo<PrdWorkOrderVo> selectPageList(PrdWorkOrderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PrdWorkOrder> lqw = buildQueryWrapper(bo);
        Page<PrdWorkOrderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询生产工单列表（不分页）
     *
     * @param bo 查询参数
     * @return 生产工单列表
     */
    @Override
    public List<PrdWorkOrderVo> selectList(PrdWorkOrderBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据 ID 查询生产工单详情
     *
     * @param id 工单 ID
     * @return 生产工单详细信息
     */
    @Override
    public PrdWorkOrderVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 根据工单 ID 查询工单实体
     *
     * @param woId 工单 ID
     * @return 生产工单实体
     */
    @Override
    public PrdWorkOrder selectPrdWorkOrderByWoId(Long woId) {
        return baseMapper.selectById(woId);
    }

    /**
     * 查询生产工单列表（按实体条件）
     *
     * @param prdWorkOrder 工单查询实体
     * @return 生产工单列表
     */
    @Override
    public List<PrdWorkOrder> selectPrdWorkOrderList(PrdWorkOrder prdWorkOrder) {
        LambdaQueryWrapper<PrdWorkOrder> lqw = buildEntityQueryWrapper(prdWorkOrder);
        return baseMapper.selectList(lqw);
    }

    /**
     * 新增生产工单
     *
     * @param prdWorkOrder 生产工单实体
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertPrdWorkOrder(PrdWorkOrder prdWorkOrder) {
        return baseMapper.insert(prdWorkOrder);
    }

    /**
     * 新增生产工单（通过 BO）
     * <p>
     * 初始状态设为 CREATED，自动生成工单编号。
     *
     * @param bo 生产工单业务对象
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PrdWorkOrderBo bo) {
        PrdWorkOrder entity = MapstructUtils.convert(bo, PrdWorkOrder.class);
        entity.setWoStatus(STATUS_CREATED);
        return baseMapper.insert(entity);
    }

    /**
     * 修改生产工单（通过 BO）
     * <p>
     * 仅 CREATED 或 APPROVED 状态可编辑。
     *
     * @param bo 生产工单业务对象
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PrdWorkOrderBo bo) {
        PrdWorkOrder entity = MapstructUtils.convert(bo, PrdWorkOrder.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 修改生产工单
     *
     * @param prdWorkOrder 生产工单实体
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePrdWorkOrder(PrdWorkOrder prdWorkOrder) {
        return baseMapper.updateById(prdWorkOrder);
    }

    /**
     * 批量删除生产工单
     *
     * @param ids 要删除的工单 ID 数组
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 根据工单 ID 数组批量删除生产工单
     *
     * @param woIds 工单 ID 数组
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePrdWorkOrderByWoIds(Long[] woIds) {
        return baseMapper.deleteBatchIds(List.of(woIds));
    }

    /**
     * 根据工单 ID 删除生产工单
     *
     * @param woId 工单 ID
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePrdWorkOrderByWoId(Long woId) {
        return baseMapper.deleteById(woId);
    }

    /**
     * 下达工单
     * <p>
     * 将工单状态从 CREATED 或 APPROVED 更新为 RELEASED，自动生成生产领料单（排除倒冲物料）。
     *
     * @param woId 工单 ID
     */
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

        // 检查工单所需物料的库存可用性 (BOM 展开 + 库存检查, 仅告警不阻断)
        checkMaterialAvailabilityOnRelease(wo);

        // ★ 新增：工单下达时自动生成生产领料单（排除倒冲物料）
        createProductionIssueOnRelease(wo);
    }

    /**
     * 工单下达时检查 BOM 所需物料的库存可用性 (仅告警不阻断)
     */
    private void checkMaterialAvailabilityOnRelease(PrdWorkOrder wo) {
        if (wo.getBomId() == null || wo.getPlannedQty() == null) {
            return;
        }
        List<IndentedBomLineVo> bomLines = bomService.explodeBom(wo.getBomId());
        for (IndentedBomLineVo bomLine : bomLines) {
            if (bomLine.getMaterialId() == null || bomLine.getCumulativeQuantity() == null) {
                continue;
            }
            BigDecimal requiredQty = bomLine.getCumulativeQuantity().multiply(wo.getPlannedQty());
            BigDecimal available = stockReservationService.checkAvailable(bomLine.getMaterialId(), null);
            if (available.compareTo(requiredQty) < 0) {
                log.warn("工单 {} 物料 {} 库存不足: 需求={}, 可用={}",
                    wo.getWoId(), bomLine.getMaterialId(), requiredQty, available);
            }
        }
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
        issue.setIssueCode(autoCodeUtil.genSerialCode("WM_PROD_ISSUE_CODE", null));
        issue.setWoId(wo.getWoId());
        issue.setWoNo(wo.getWoNo());
        issue.setIssueDate(new Date());
        issue.setStatus("CREATED");
        issue.setIssueType("NORMAL");
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
                // BOM 驱动的应领/已领/本次/欠领语义，支持后续多次部分领料
                line.setBomQty(detail.getBomQuantity());
                line.setLossRate(detail.getLossRate());
                line.setRequiredQty(detail.getRequiredQuantity());
                line.setIssuedQty(BigDecimal.ZERO);
                line.setCurrentQty(detail.getRequiredQuantity());
                line.setShortQty(detail.getRequiredQuantity());
                line.setRequirementDetailId(detail.getDetailId());
                line.setLineStatus("PENDING");
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

    /**
     * 开始生产
     * <p>
     * 将工单状态从 RELEASED 更新为 IN_PROGRESS，非倒冲工单需校验是否已生成领料单。
     *
     * @param woId 工单 ID
     */
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

    /**
     * 完工
     * <p>
     * 将工单状态从 IN_PROGRESS 更新为 COMPLETED，执行完工倒冲领料并生成成品入库记录。
     *
     * @param woId 工单 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long woId) {
        PrdWorkOrder wo = baseMapper.selectById(woId);
        if (wo == null) {
            throw new RuntimeException("工单不存在: " + woId);
        }
        // 保留原行为: 默认取计划数量作为完工数量
        BigDecimal planned = wo.getPlannedQty() != null ? wo.getPlannedQty() : BigDecimal.ZERO;
        complete(woId, planned, BigDecimal.ZERO);
    }

    /**
     * 工单完工(指定完工数量)
     * <p>
     * 使用原子更新避免并发覆盖: 状态置 COMPLETED + 累加完工/报废数量 + 完工时间,
     * 并发送 ProductionCompleteMessage 触发倒冲领料 + 成品入库。
     *
     * @param woId        工单 ID
     * @param completeQty 本次完工数量
     * @param scrapQty    本次报废数量
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long woId, BigDecimal completeQty, BigDecimal scrapQty) {
        PrdWorkOrder wo = baseMapper.selectById(woId);
        if (wo == null) {
            throw new RuntimeException("工单不存在: " + woId);
        }
        WoStatusEnum currentStatus = WoStatusEnum.fromCode(wo.getWoStatus());
        if (currentStatus == WoStatusEnum.COMPLETED || currentStatus == WoStatusEnum.CLOSED) {
            throw new RuntimeException("工单已完工或已关闭, 不可重复完工");
        }
        if (!currentStatus.canTransitionTo(WoStatusEnum.COMPLETED)) {
            throw new RuntimeException("工单状态[" + currentStatus.getLabel() + "]不可完工");
        }
        // 原子更新: 状态 + 累加完工数量 + 累加报废数量 + 完工时间
        LambdaUpdateWrapper<PrdWorkOrder> wrapper = Wrappers.<PrdWorkOrder>lambdaUpdate()
            .eq(PrdWorkOrder::getWoId, woId)
            .set(PrdWorkOrder::getWoStatus, WoStatusEnum.COMPLETED.getCode())
            .set(PrdWorkOrder::getActualEndDate, new Date())
            .setSql(completeQty != null, "completed_qty = completed_qty + " + completeQty)
            .setSql(scrapQty != null, "scrap_qty = scrap_qty + " + scrapQty);
        int rows = baseMapper.update(null, wrapper);
        if (rows == 0) {
            throw new RuntimeException("工单完工更新失败, 可能已被其他操作修改");
        }
        // 触发完工倒冲入库链路(保留原 complete 副作用)
        BigDecimal done = completeQty != null ? completeQty : BigDecimal.ZERO;
        ProductionCompleteMessage completeMsg = new ProductionCompleteMessage();
        completeMsg.setWoId(woId);
        completeMsg.setCompletionQty(done);
        completeMsg.setMaterialId(wo.getMaterialId());
        completeMsg.setMaterialCode(wo.getMaterialCode());
        completeMsg.setMaterialName(wo.getMaterialName());
        completeMsg.setWorkshopId(wo.getWorkshopId());
        completeMsg.setSourceModule("prd");
        // 携带租户ID，供MQ消费者恢复租户上下文
        String tid = TenantHelper.getTenantId();
        if (tid != null) {
            completeMsg.setTenantId(Long.parseLong(tid));
        }
        messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE,
            RoutingKeyConstants.PRODUCTION_COMPLETE_BACKFLUSH, completeMsg);
    }

    /**
     * 关闭工单
     * <p>
     * 将工单状态更新为 CLOSED，生产中状态工单不能直接关闭，需先完工。
     *
     * @param woId 工单 ID
     */
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

    /**
     * 发起审批流程
     * <p>
     * 仅新建状态的工单可提交审批，启动 Flowable 流程实例。
     *
     * @param woIdStr      工单 ID 字符串
     * @param selectFlowId 选择的流程模板 ID
     * @return 是否提交成功
     */
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

    /**
     * 获取流程实例 ID
     * <p>
     * 依次从业务表、运行时流程实例、历史流程实例中查询流程实例 ID。
     *
     * @param woIdStr 工单 ID 字符串
     * @return 流程实例 ID，未找到返回 {@code null}
     */
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
            // 审批通过后，事务提交后再发送工单下达消息到MES（确保ERP状态已持久化）
            final PrdWorkOrder msgEntity = entity;
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        sendWorkOrderReleaseMessage(msgEntity);
                    }
                });
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            entity.setWoStatus(STATUS_CREATED);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            entity.setWoStatus(STATUS_CREATED);
        }
        baseMapper.updateById(entity);
    }

    /**
     * 发送工单下达消息到MES队列
     */
    private void sendWorkOrderReleaseMessage(PrdWorkOrder entity) {
        try {
            WorkOrderReleaseMessage message = new WorkOrderReleaseMessage();
            message.setWoId(entity.getWoId());
            message.setWoNo(entity.getWoNo());
            message.setMaterialId(entity.getMaterialId());
            message.setMaterialCode(entity.getMaterialCode());
            message.setMaterialName(entity.getMaterialName());
            message.setPlannedQty(entity.getPlannedQty());
            message.setPlannedStartDate(entity.getPlanStartDate());
            message.setPlannedEndDate(entity.getPlanEndDate());
            message.setWorkshopId(entity.getWorkshopId());
            message.setBomId(entity.getBomId());
            messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE, RoutingKeyConstants.MES_WORKORDER_RELEASE, message);
            log.info("工单下达消息已发送: woId={}, woNo={}", entity.getWoId(), entity.getWoNo());
        } catch (Exception e) {
            log.error("工单下达消息发送失败: woId={}", entity.getWoId(), e);
            // 消息发送失败不影响审批状态更新，补偿任务会重新投递
        }
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
        LambdaQueryWrapper<PrdWorkOrder> lqw = Wrappers.<PrdWorkOrder>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getWoNo()), PrdWorkOrder::getWoNo, bo.getWoNo())
            .like(StringUtils.isNotBlank(bo.getPlanName()), PrdWorkOrder::getPlanName, bo.getPlanName())
            .eq(StringUtils.isNotBlank(bo.getSourceType()), PrdWorkOrder::getSourceType, bo.getSourceType())
            .eq(bo.getSourceId() != null, PrdWorkOrder::getSourceId, bo.getSourceId())
            .eq(StringUtils.isNotBlank(bo.getSourceNo()), PrdWorkOrder::getSourceNo, bo.getSourceNo())
            .eq(bo.getSourceLineId() != null, PrdWorkOrder::getSourceLineId, bo.getSourceLineId())
            .eq(bo.getMaterialId() != null, PrdWorkOrder::getMaterialId, bo.getMaterialId())
            .like(StringUtils.isNotBlank(bo.getMaterialName()), PrdWorkOrder::getMaterialName, bo.getMaterialName())
            .eq(StringUtils.isNotBlank(bo.getWoStatus()), PrdWorkOrder::getWoStatus, bo.getWoStatus())
            .eq(bo.getWorkshopId() != null, PrdWorkOrder::getWorkshopId, bo.getWorkshopId())
            .like(StringUtils.isNotBlank(bo.getWorkshopName()), PrdWorkOrder::getWorkshopName, bo.getWorkshopName())
            .eq(bo.getWorkstationId() != null, PrdWorkOrder::getWorkstationId, bo.getWorkstationId())
            .like(StringUtils.isNotBlank(bo.getWorkstationName()), PrdWorkOrder::getWorkstationName, bo.getWorkstationName())
            .eq(bo.getProductionLineId() != null, PrdWorkOrder::getProductionLineId, bo.getProductionLineId())
            .eq(bo.getResponsibleId() != null, PrdWorkOrder::getResponsibleId, bo.getResponsibleId())
            .like(StringUtils.isNotBlank(bo.getResponsibleName()), PrdWorkOrder::getResponsibleName, bo.getResponsibleName())
            .eq(bo.getTeamId() != null, PrdWorkOrder::getTeamId, bo.getTeamId())
            .like(StringUtils.isNotBlank(bo.getTeamName()), PrdWorkOrder::getTeamName, bo.getTeamName())
            .eq(bo.getPriority() != null, PrdWorkOrder::getPriority, bo.getPriority());
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
