package com.nexus.erp.prd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdRouting;
import com.nexus.erp.bd.domain.BdWorkshop;
import com.nexus.erp.bd.domain.vo.BdBomVo;
import com.nexus.erp.bd.domain.vo.IndentedBomLineVo;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdRoutingMapper;
import com.nexus.erp.bd.mapper.BdWorkshopMapper;
import com.nexus.erp.bd.service.IBdBomService;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.PrdProductionPlan;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdWorkOrderBo;
import com.nexus.erp.prd.domain.enums.WoStatusEnum;
import com.nexus.erp.prd.domain.vo.PrdWorkOrderVo;
import com.nexus.erp.prd.mapper.PrdProductionPlanMapper;
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
import com.nexus.erp.so.service.ISoFulfillmentWritebackService;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import lombok.extern.slf4j.Slf4j;

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
import java.util.Objects;

/**
 * 生产工单服务业务层实现
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
    @org.springframework.beans.factory.annotation.Autowired
    private com.nexus.erp.wm.service.WmSerialInventoryService serialInventory;

    @org.springframework.beans.factory.annotation.Autowired
    private com.nexus.erp.prd.mapper.ProductionCompletionPostingMapper manifestSourceMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private com.nexus.erp.prd.service.PrdWorkOrderTrackingPolicyService trackingPolicies;

    @org.springframework.beans.factory.annotation.Value("${nexus.trace.relay.enabled:false}")
    private boolean traceRelayEnabled;

    /**
     * 生产计划状态：已确认（工单审批通过后据此自动下达）
     */
    private static final String PLAN_STATUS_CONFIRMED = "CONFIRMED";

    /**
     * 生产计划状态：已下达
     */
    private static final String PLAN_STATUS_RELEASED = "RELEASED";

    /**
     * 生产计划状态：已完工
     */
    private static final String PLAN_STATUS_COMPLETED = "COMPLETED";

    private final PrdWorkOrderMapper baseMapper;
    private final PrdProductionPlanMapper prdProductionPlanMapper;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final FlowableProcInsIdHelper procInsIdHelper;
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
    private final BdMaterialMapper bdMaterialMapper;
    private final BdRoutingMapper bdRoutingMapper;
    private final BdWorkshopMapper bdWorkshopMapper;
    private final ISoFulfillmentWritebackService fulfillmentWritebackService;

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
        fillDeptInfo(page.getRecords());
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
        List<PrdWorkOrderVo> list = baseMapper.selectVoList(buildQueryWrapper(bo));
        fillDeptInfo(list);
        return list;
    }

    /**
     * 根据 ID 查询生产工单详情
     *
     * @param id 工单 ID
     * @return 生产工单详细信息
     */
    @Override
    public PrdWorkOrderVo selectById(Long id) {
        PrdWorkOrderVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillDeptInfo(List.of(vo));
        }
        return vo;
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
     * 虚拟件物料不生成工单（虚拟件跳层，不入库不生产）。
     *
     * @param bo 生产工单业务对象
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PrdWorkOrderBo bo) {
        /**
         * 虚拟件校验：虚拟件不生成工单
         */
        if (bo.getMaterialId() != null) {
            BdMaterial material = bdMaterialMapper.selectById(bo.getMaterialId());
            if (material != null && Integer.valueOf(1).equals(material.getIsPhantom())) {
                throw new RuntimeException("物料[" + material.getMaterialCode() + "]为虚拟件，不生成工单");
            }
        }
        PrdWorkOrder entity = MapstructUtils.convert(bo, PrdWorkOrder.class);
        if (StringUtils.isBlank(entity.getWoNo())) {
            entity.setWoNo(autoCodeUtil.genSerialCode("PRD_WORK_ORDER_CODE", null));
        }
        entity.setWoStatus(STATUS_CREATED);
        int rows = baseMapper.insert(entity);
        /**
         * 回填主键，供控制器 selectById(bo.getWoId()) 取回保存结果
         */
        bo.setWoId(entity.getWoId());
        return rows;
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
        PrdMaterialRequirement sourceRequirement = resolveSourceRequirementOnRelease(wo);
        /**
         * 提交前解析并固定专属路线，错绑或冲突不得认领状态及生成MES快照。
         */
        final Long routingId = resolveRoutingIdOnRelease(wo);
        /**
         * 新追溯链路必须在原ERP下达事务冻结策略，失败不得认领状态或发布MES消息。
         */
        if (traceRelayEnabled) {
            trackingPolicies.freezeForRelease(wo);
        }
        /**
         * 认领下达状态；并发请求即使都读到旧状态，也只有一方能继续生成领料单和 MES 消息。
         */
        LambdaUpdateWrapper<PrdWorkOrder> claim =
                Wrappers.<PrdWorkOrder>lambdaUpdate()
                        .eq(PrdWorkOrder::getWoId, woId)
                        .eq(PrdWorkOrder::getWoStatus, wo.getWoStatus())
                        .eq(
                                StringUtils.isNotBlank(wo.getTenantId()),
                                PrdWorkOrder::getTenantId,
                                wo.getTenantId())
                        .set(PrdWorkOrder::getWoStatus, STATUS_RELEASED);
        if (baseMapper.update(null, claim) != 1) {
            throw new RuntimeException("工单状态已变更，下达失败，请刷新后重试");
        }
        wo.setWoStatus(STATUS_RELEASED);

        /**
         * 检查工单所需物料的库存可用性 (BOM 展开 + 库存检查, 仅告警不阻断)
         */
        checkMaterialAvailabilityOnRelease(wo);

        /**
         * 只生成本工单来源需求的领料单（排除倒冲物料），不借用同计划其他工单的需求。
         */
        createProductionIssueOnRelease(wo, sourceRequirement);

        /**
         * 事务提交后发送工单下达消息到MES队列
         */
        final PrdWorkOrder msgEntity = wo;
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {
                            /**
                             * 事务提交后回调：发送工单下达消息到 MES 队列
                             */
                            @Override
                            public void afterCommit() {
                                sendWorkOrderReleaseMessage(msgEntity, routingId);
                            }
                        });
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
            BigDecimal available =
                    stockReservationService.checkAvailable(bomLine.getMaterialId(), null);
            if (available.compareTo(requiredQty) < 0) {
                log.warn(
                        "工单 {} 物料 {} 库存不足: 需求={}, 可用={}",
                        wo.getWoId(),
                        bomLine.getMaterialId(),
                        requiredQty,
                        available);
            }
        }
    }

    /**
     * 仅从实际工单物料、BOM和同租户当前专属路线解析消息路线，不接受客户端路线ID。
     * 历史工单缺少物料/租户，或未绑定路线且没有专属路线时保留原空值行为；
     * 不借用通用路线，不另造版本/日期回退策略，也不把空路线视为可制造证明。
     */
    private Long resolveRoutingIdOnRelease(PrdWorkOrder wo) {
        if (wo.getMaterialId() == null || StringUtils.isBlank(wo.getTenantId())) {
            return null;
        }
        Long bomRoutingId = null;
        if (wo.getBomId() != null) {
            BdBomVo bom = bomService.selectById(wo.getBomId());
            if (bom == null) {
                throw new RuntimeException("工单BOM不存在，禁止下达");
            }
            if (!Objects.equals(wo.getBomId(), bom.getBomId())) {
                throw new RuntimeException("工单BOM关联不一致，禁止下达");
            }
            if (!Objects.equals(wo.getMaterialId(), bom.getParentMaterialId())) {
                throw new RuntimeException("BOM与工单物料不一致，禁止下达");
            }
            bomRoutingId = bom.getRoutingId();
        }
        List<BdRouting> routings =
                bdRoutingMapper.selectList(
                        Wrappers.<BdRouting>lambdaQuery()
                                .eq(BdRouting::getMaterialId, wo.getMaterialId())
                                .eq(BdRouting::getTenantId, wo.getTenantId())
                                .eq(BdRouting::getIsCurrent, 1L)
                                .eq(BdRouting::getStatus, 1L)
                                .eq(BdRouting::getDelFlag, "0"));
        if (routings == null) {
            throw new RuntimeException("工单专属路线查询结果缺失，禁止下达");
        }
        if (routings.size() > 1) {
            throw new RuntimeException("工单物料存在多个已启用当前专属路线，禁止下达");
        }
        if (routings.isEmpty()) {
            if (bomRoutingId != null) {
                throw new RuntimeException("BOM绑定路线不是工单物料的已启用当前专属路线，禁止下达");
            }
            log.warn(
                    "工单未配置当前专属路线，保留历史空路线消息: woId={}, materialId={}",
                    wo.getWoId(),
                    wo.getMaterialId());
            return null;
        }
        BdRouting routing = routings.get(0);
        if (routing == null
                || routing.getRoutingId() == null
                || routing.getRoutingId() <= 0
                || !Objects.equals(wo.getMaterialId(), routing.getMaterialId())
                || !Objects.equals(wo.getTenantId(), routing.getTenantId())
                || !Long.valueOf(1L).equals(routing.getIsCurrent())
                || !Long.valueOf(1L).equals(routing.getStatus())
                || !"0".equals(routing.getDelFlag())) {
            throw new RuntimeException("工单当前专属路线归属或状态无效，禁止下达");
        }
        if (bomRoutingId != null && !Objects.equals(bomRoutingId, routing.getRoutingId())) {
            throw new RuntimeException("BOM绑定路线与工单物料当前专属路线不一致，禁止下达");
        }
        return routing.getRoutingId();
    }

    /**
     * 下达前核实 MRP 来源；PLAN、销售等来源的 sourceId 不能当作物料需求 ID。
     */
    private PrdMaterialRequirement resolveSourceRequirementOnRelease(PrdWorkOrder wo) {
        if (!"MATERIAL_REQUIREMENT".equals(wo.getSourceType())) {
            return null;
        }
        if (wo.getSourceId() == null
                || wo.getPlanId() == null
                || StringUtils.isBlank(wo.getTenantId())) {
            throw new RuntimeException("工单来源物料需求关联不完整，请人工核查后再下达");
        }
        PrdMaterialRequirement requirement = requirementMapper.selectById(wo.getSourceId());
        if (requirement == null
                || !Objects.equals(wo.getPlanId(), requirement.getPlanId())
                || !Objects.equals(wo.getTenantId(), requirement.getTenantId())) {
            throw new RuntimeException("工单来源物料需求不存在或与工单计划、租户不匹配，请人工核查");
        }
        return requirement;
    }

    /**
     * 工单下达时仅按已核验的来源需求创建生产领料单
     */
    private void createProductionIssueOnRelease(
            PrdWorkOrder wo, PrdMaterialRequirement requirement) {
        if (requirement == null) {
            return;
        }
        /**
         * 倒冲工单不在下达时生成预领料单（完工时按BOM倒冲扣料），避免与完工倒冲重复扣料
         */
        if (Integer.valueOf(1).equals(wo.getBackflushFlag())) {
            log.info("工单[{}]为倒冲工单，下达时不生成预领料单，完工时倒冲扣料", wo.getWoNo());
            return;
        }
        /**
         * 创建领料单头
         */
        WmProductionIssue issue = new WmProductionIssue();
        issue.setIssueCode(autoCodeUtil.genSerialCode("WM_PROD_ISSUE_CODE", null));
        issue.setTenantId(wo.getTenantId());
        issue.setWoId(wo.getWoId());
        issue.setWoNo(wo.getWoNo());
        issue.setIssueDate(new Date());
        issue.setStatus("CREATED");
        issue.setIssueType("NORMAL");
        issue.setRemark("工单下达自动生成；原料领料仓库待人工选择");
        productionIssueMapper.insert(issue);

        /**
         * 创建领料单行（排除倒冲物料）
         */
        boolean hasLines = false;
        LambdaQueryWrapper<PrdMaterialRequirementDetail> detailLqw = Wrappers.lambdaQuery();
        detailLqw
                .eq(PrdMaterialRequirementDetail::getRequirementId, requirement.getRequirementId())
                .eq(PrdMaterialRequirementDetail::getTenantId, wo.getTenantId());
        List<PrdMaterialRequirementDetail> details = detailMapper.selectList(detailLqw);

        for (PrdMaterialRequirementDetail detail : details) {
            /**
             * 倒冲物料不生成领料单行（完工时自动倒冲）
             */
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
            /**
             * BOM 驱动的应领/已领/本次/欠领语义，支持后续多次部分领料
             */
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

        if (hasLines) {
            log.info("工单[{}]下达后自动生成领料单[{}]", wo.getWoNo(), issue.getIssueCode());
        } else {
            /**
             * 无领料行则删除空领料单
             */
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

        /**
         * ★ 新增：校验非倒冲工单是否已生成领料单
         */
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
     * 标记工单为生产中(轻量状态流转)
     * <p>
     * 仅将 RELEASED 状态工单置为 IN_PROGRESS 并记录实际开工时间，不校验领料单。
     * 供 MES 完工回写等权威外部信号触发完工时前置调用，使状态机合法
     * (RELEASED → IN_PROGRESS → COMPLETED)。对已处于 IN_PROGRESS 的工单幂等。
     *
     * @param woId 工单 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markInProgress(Long woId) {
        PrdWorkOrder wo = baseMapper.selectById(woId);
        if (wo == null) {
            throw new RuntimeException("工单不存在: " + woId);
        }
        if (STATUS_IN_PROGRESS.equals(wo.getWoStatus())) {
            return;
        }
        if (!STATUS_RELEASED.equals(wo.getWoStatus())) {
            throw new RuntimeException("仅已下达状态工单可标记生产中,当前状态:" + wo.getWoStatus());
        }
        wo.setWoStatus(STATUS_IN_PROGRESS);
        wo.setActualStartDate(new Date());
        baseMapper.updateById(wo);
    }

    /**
     * 完工
     * <p>
     * 将工单状态从 IN_PROGRESS 更新为 COMPLETED，并异步触发完工入库；生产完工不代表正常领料已过账。
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
        /**
         * 保留原行为: 默认取计划数量作为完工数量
         */
        BigDecimal planned = wo.getPlannedQty() != null ? wo.getPlannedQty() : BigDecimal.ZERO;
        complete(woId, planned, BigDecimal.ZERO);
    }

    /**
     * 工单完工(指定完工数量)
     * <p>
     * 使用原子更新避免并发覆盖: 状态置 COMPLETED + 累加完工/报废数量 + 完工时间,
     * 并发送 ProductionCompleteMessage 触发倒冲领料 + 成品入库。
     * 非倒冲工单存在未完成领料单时记录告警，保留真实完工事实，不代填仓库、BOM 单位或数量。
     *
     * @param woId        工单 ID
     * @param completeQty 本次完工数量
     * @param scrapQty    本次报废数量
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long woId, BigDecimal completeQty, BigDecimal scrapQty) {
        completeInternal(woId, completeQty, scrapQty, true, null);
    }

    /**
     * 仅由冻结清单ERP入口调用；调用者已持有真实工单行锁且同事务复核清单。
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeFromManifest(com.nexus.common.trace.CompletionManifest manifest) {
        if (manifest == null
                || !java.util.Set.of("FROZEN", "APPLIED", "RECEIVED").contains(manifest.status())
                || !java.util.Objects.equals(TenantHelper.getTenantId(), manifest.tenantId())
                || manifest.goodQty() == null
                || manifest.goodQty().signum() < 0
                || manifest.scrapQty() == null
                || manifest.scrapQty().signum() < 0
                || manifest.goodQty().add(manifest.scrapQty()).signum() <= 0) {
            throw new com.nexus.common.core.exception.ServiceException("最终冻结清单来源或数量无效");
        }
        PrdWorkOrder wo =
                manifestSourceMapper.lockWorkOrder(TenantHelper.getTenantId(), manifest.erpWoId());
        if (wo == null
                || !java.util.Objects.equals(wo.getMaterialId(), manifest.materialId())
                || wo.getPlannedQty() == null
                || wo.getCompletedQty() == null
                || wo.getScrapQty() == null
                || wo.getCompletedQty()
                                .add(wo.getScrapQty())
                                .add(manifest.goodQty())
                                .add(manifest.scrapQty())
                                .compareTo(wo.getPlannedQty())
                        > 0) {
            throw new com.nexus.common.core.exception.ServiceException("清单物料、原计划或剩余可完成数量不符");
        }
        completeInternal(manifest.erpWoId(), manifest.goodQty(), manifest.scrapQty(), false, wo);
    }

    private void completeInternal(
            Long woId,
            BigDecimal completeQty,
            BigDecimal scrapQty,
            boolean publishLegacy,
            PrdWorkOrder locked) {
        PrdWorkOrder wo = locked != null ? locked : baseMapper.selectById(woId);
        if (wo == null) {
            throw new RuntimeException("工单不存在: " + woId);
        }
        if (publishLegacy
                && serialInventory != null
                && serialInventory.isStrictSerial(wo.getMaterialId())) {
            throw new com.nexus.common.core.exception.ServiceException(
                    "STRICT单件工单必须由最终冻结清单完成，不能触发旧数量MQ路径");
        }
        WoStatusEnum currentStatus = WoStatusEnum.fromCode(wo.getWoStatus());
        if (currentStatus == null) {
            throw new RuntimeException("工单状态为空, 无法完工: " + woId);
        }
        if (currentStatus == WoStatusEnum.COMPLETED || currentStatus == WoStatusEnum.CLOSED) {
            throw new RuntimeException("工单已完工或已关闭, 不可重复完工");
        }
        if (!currentStatus.canTransitionTo(WoStatusEnum.COMPLETED)) {
            throw new RuntimeException("工单状态[" + currentStatus.getLabel() + "]不可完工");
        }
        /**
         * 原子更新: 状态 + 累加完工数量 + 累加报废数量 + 完工时间
         * 使用 toPlainString() 避免 BigDecimal 科学计数法导致 SQL 语法错误
         * 并发防重: WHERE 带状态条件(仅非 COMPLETED/CLOSED 可更新), 与预读校验配合杜绝 MQ 重投+手动完工的双完工
         */
        String completeSetSql =
                completeQty != null && completeQty.compareTo(BigDecimal.ZERO) != 0
                        ? "completed_qty = completed_qty + " + completeQty.toPlainString()
                        : null;
        String scrapSetSql =
                scrapQty != null && scrapQty.compareTo(BigDecimal.ZERO) != 0
                        ? "scrap_qty = scrap_qty + " + scrapQty.toPlainString()
                        : null;
        LambdaUpdateWrapper<PrdWorkOrder> wrapper =
                Wrappers.<PrdWorkOrder>lambdaUpdate()
                        .eq(PrdWorkOrder::getWoId, woId)
                        .set(PrdWorkOrder::getWoStatus, WoStatusEnum.COMPLETED.getCode())
                        .set(PrdWorkOrder::getActualEndDate, new Date())
                        .setSql(completeSetSql != null, completeSetSql)
                        .setSql(scrapSetSql != null, scrapSetSql)
                        .notIn(
                                PrdWorkOrder::getWoStatus,
                                WoStatusEnum.COMPLETED.getCode(),
                                STATUS_CLOSED);
        int rows = baseMapper.update(null, wrapper);
        if (rows == 0) {
            throw new RuntimeException("工单已完工或已关闭, 不可重复完工");
        }

        /**
         * 回写销售订单行已投产数量：wo.planId → plan.orderLineId → SO 行
         * 仅当工单关联生产计划、且计划关联销售订单行时回写（覆盖 MQ 完工与手动完工两条路径）
         */
        if (wo.getPlanId() != null
                && completeQty != null
                && completeQty.compareTo(BigDecimal.ZERO) > 0) {
            PrdProductionPlan plan = prdProductionPlanMapper.selectById(wo.getPlanId());
            if (plan != null && plan.getOrderLineId() != null) {
                fulfillmentWritebackService.addProducedQty(plan.getOrderLineId(), completeQty);
            }
            /**
             * 计划下所有工单完工时，自动更新计划状态为 COMPLETED
             */
            if (plan != null && PLAN_STATUS_RELEASED.equals(plan.getStatus())) {
                Long unfinishedCount =
                        baseMapper.selectCount(
                                Wrappers.<PrdWorkOrder>lambdaQuery()
                                        .eq(PrdWorkOrder::getPlanId, wo.getPlanId())
                                        .notIn(
                                                PrdWorkOrder::getWoStatus,
                                                WoStatusEnum.COMPLETED.getCode(),
                                                STATUS_CLOSED));
                if (unfinishedCount == 0) {
                    LambdaUpdateWrapper<PrdProductionPlan> planUw =
                            Wrappers.<PrdProductionPlan>lambdaUpdate()
                                    .eq(PrdProductionPlan::getPlanId, wo.getPlanId())
                                    .eq(PrdProductionPlan::getStatus, PLAN_STATUS_RELEASED)
                                    .set(PrdProductionPlan::getStatus, PLAN_STATUS_COMPLETED);
                    prdProductionPlanMapper.update(null, planUw);
                    log.info("生产计划完工: planId={}, planNo={}", plan.getPlanId(), plan.getPlanNo());
                }
            }
        }

        warnIncompleteNormalIssues(wo);

        if (!publishLegacy) {
            return;
        }

        /**
         * 触发完工倒冲入库链路(保留原 complete 副作用)
         */
        BigDecimal done = completeQty != null ? completeQty : BigDecimal.ZERO;
        ProductionCompleteMessage completeMsg = new ProductionCompleteMessage();
        completeMsg.setWoId(woId);
        completeMsg.setWoNo(wo.getWoNo());
        completeMsg.setCompletionQty(done);
        completeMsg.setMaterialId(wo.getMaterialId());
        completeMsg.setMaterialCode(wo.getMaterialCode());
        completeMsg.setMaterialName(wo.getMaterialName());
        completeMsg.setWorkshopId(wo.getWorkshopId());
        completeMsg.setSourceModule("prd");
        /**
         * 携带租户ID，供MQ消费者恢复租户上下文
         */
        String tid = TenantHelper.getTenantId();
        if (tid != null) {
            completeMsg.setTenantId(Long.parseLong(tid));
        }
        messageProducer.send(
                ExchangeConstants.TOPIC_EXCHANGE,
                RoutingKeyConstants.PRODUCTION_COMPLETE_BACKFLUSH,
                completeMsg);
    }

    /**
     * 对非倒冲工单的未完成领料单记录明确告警，不改写生产或库存事实。
     * <p>
     * MES 完工是生产完成的权威信号，但 CREATED/PARTIAL 领料单仍需人工核验并执行。
     * 本检查只读现有单据，不补仓库、BOM 单位或领料数量，也不把单据标记为已过账。
     * 查询必须绑定当前租户上下文；租户缺失或与工单不一致时拒绝辅助查询并记录固定告警。
     * 辅助核验的运行时异常仅记录无法核验，不阻断可信完工主写及后续消息发送；
     * 完工主写、消息发送异常及严重系统错误仍按原语义传播，不由本方法吞掉。
     *
     * @param wo 已完工的生产工单
     */
    private void warnIncompleteNormalIssues(PrdWorkOrder wo) {
        if (Integer.valueOf(1).equals(wo.getBackflushFlag())) {
            return;
        }
        String tenantId = null;
        List<WmProductionIssue> pendingIssues;
        try {
            tenantId = TenantHelper.getTenantId();
            if (StringUtils.isBlank(tenantId)) {
                warnNormalIssueCheckUnavailable(wo, tenantId, "TENANT_CONTEXT_MISSING", null);
                return;
            }
            if (StringUtils.isNotBlank(wo.getTenantId())
                    && !Objects.equals(wo.getTenantId(), tenantId)) {
                warnNormalIssueCheckUnavailable(wo, tenantId, "TENANT_MISMATCH", null);
                return;
            }
            pendingIssues =
                    productionIssueMapper.selectList(
                            Wrappers.<WmProductionIssue>lambdaQuery()
                                    .eq(WmProductionIssue::getWoId, wo.getWoId())
                                    .eq(WmProductionIssue::getTenantId, tenantId)
                                    .in(WmProductionIssue::getStatus, "CREATED", "PARTIAL"));
        } catch (RuntimeException failure) {
            warnNormalIssueCheckUnavailable(
                    wo, tenantId, "READ_CHECK_FAILED", failure.getClass().getSimpleName());
            return;
        }
        for (WmProductionIssue issue : pendingIssues) {
            log.warn(
                    "PRODUCTION_MATERIAL_UNPOSTED: 生产已完工但领料尚未完成过账: woId={}, woNo={}, issueId={},"
                            + " issueCode={}, issueStatus={}, issueType={}, warehouseId={}；"
                            + "正常领料不会自动倒冲，请人工核验BOM单位、数量及领料仓库后处理，禁止自动补齐或把生产完工视为物料过账完成",
                    wo.getWoId(),
                    wo.getWoNo(),
                    issue.getIssueId(),
                    issue.getIssueCode(),
                    issue.getStatus(),
                    issue.getIssueType(),
                    issue.getWarehouseId());
        }
    }

    /**
     * 记录辅助领料核验无法完成的固定告警，避免原始异常正文泄露 SQL 或连接信息。
     *
     * @param wo 当前工单
     * @param tenantId 当前租户上下文，可为空但不得据此执行无范围查询
     * @param reason 固定原因码
     * @param errorType 异常类型，可为空，不记录原始异常正文或堆栈
     */
    private void warnNormalIssueCheckUnavailable(
            PrdWorkOrder wo, String tenantId, String reason, String errorType) {
        log.warn(
                "PRODUCTION_MATERIAL_CHECK_UNAVAILABLE: 无法核验正常领料过账状态，不阻断生产完工及后续消息处理: "
                        + "woId={}, woNo={}, tenantId={}, reason={}, errorType={}",
                wo.getWoId(),
                wo.getWoNo(),
                tenantId,
                reason,
                errorType);
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

    /**
     * Flowable 工作流集成。
     */

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
        R<Void> result =
                processService.startProcessByDataId(
                        woIdStr, selectFlowId, "prdWorkOrderService", variables);
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
        return procInsIdHelper.resolve(
                woIdStr,
                () -> {
                    WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(woIdStr);
                    return business != null ? business.getProcessInstanceId() : null;
                });
    }

    /**
     * 工作流审批结果回调
     * <p>根据审批状态更新工单状态（审批中/已批准/已创建），审批通过后自动下达来源生产计划。</p>
     *
     * @param business Flowable 业务对象，含流程状态与关联工单 ID
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void afterFlowHandle(WfMyBusiness business) {
        Long woId = Long.parseLong(business.getDataId());
        PrdWorkOrder entity = baseMapper.selectById(woId);
        if (entity == null) {
            return;
        }
        String previousStatus = entity.getWoStatus();
        /**
         * 已下达及后续状态不能被迟到或重复的审批回调重新打开。
         */
        if (!STATUS_CREATED.equals(previousStatus)
                && !STATUS_APPROVING.equals(previousStatus)
                && !STATUS_APPROVED.equals(previousStatus)) {
            return;
        }
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            entity.setWoStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            entity.setWoStatus(STATUS_APPROVED);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            entity.setWoStatus(STATUS_CREATED);
        } else if (ActStatus.revoke.equals(actStatus)
                || ActStatus.stop.equals(actStatus)
                || ActStatus.recall.equals(actStatus)) {
            entity.setWoStatus(STATUS_CREATED);
        } else {
            return;
        }
        /**
         * 只更新状态且校验读到的旧状态，避免与正式下达并发时用旧快照覆盖 RELEASED。
         */
        LambdaUpdateWrapper<PrdWorkOrder> update =
                Wrappers.<PrdWorkOrder>lambdaUpdate()
                        .eq(PrdWorkOrder::getWoId, woId)
                        .eq(PrdWorkOrder::getWoStatus, previousStatus)
                        .eq(
                                StringUtils.isNotBlank(entity.getTenantId()),
                                PrdWorkOrder::getTenantId,
                                entity.getTenantId())
                        .set(PrdWorkOrder::getWoStatus, entity.getWoStatus());
        if (baseMapper.update(null, update) == 1 && ActStatus.pass.equals(actStatus)) {
            /**
             * 保留审批通过后的计划自动下达；工单 MES 快照只由 release() 在正式下达后发送。
             */
            autoReleaseProductionPlan(entity.getPlanId());
        }
    }

    /**
     * 工单审批通过后自动下达来源生产计划
     * <p>
     * 幂等：仅将 CONFIRMED 状态的计划更新为 RELEASED 并记录下达时间；
     * 已下达或其他状态不做处理，避免重复审批或多工单场景下抛错影响工单状态流转。
     *
     * @param planId 来源生产计划 ID（为空则跳过）
     */
    private void autoReleaseProductionPlan(Long planId) {
        if (planId == null) {
            return;
        }
        LambdaUpdateWrapper<PrdProductionPlan> uw =
                Wrappers.<PrdProductionPlan>lambdaUpdate()
                        .eq(PrdProductionPlan::getPlanId, planId)
                        .eq(PrdProductionPlan::getStatus, PLAN_STATUS_CONFIRMED)
                        .set(PrdProductionPlan::getStatus, PLAN_STATUS_RELEASED)
                        .set(PrdProductionPlan::getReleasedTime, new Date());
        int rows = prdProductionPlanMapper.update(null, uw);
        if (rows > 0) {
            log.info("工单审批通过，自动下达生产计划: planId={}", planId);
        }
    }

    /**
     * 发送工单下达消息到MES队列
     */
    private void sendWorkOrderReleaseMessage(PrdWorkOrder entity, Long routingId) {
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
            message.setRoutingId(routingId);
            /**
             * 客户信息透传: MES 按客户+物料(+客户产品代号)匹配客户定制化发布包(mes_release_package)
             */
            message.setClientId(entity.getClientId());
            message.setClientName(entity.getClientName());
            message.setCustomerProductCode(entity.getCustomerProductCode());
            /**
             * 租户必须随消息传递(MES-MQ-01): 消费端 BaseMessageConsumer 缺失 tenantId 时直接拒绝处理，
             * 修复前不携带 tenantId 会使 MES 工单落到系统租户 000000, 且桥接查 ERP 主数据(bd_material/
             * bd_workshop/bd_routing)全空 -> 工单无单位、不创建任何工序
             */
            message.setTenantId(resolveMessageTenantId(entity.getTenantId()));
            messageProducer.send(
                    ExchangeConstants.TOPIC_EXCHANGE,
                    RoutingKeyConstants.MES_WORKORDER_RELEASE,
                    message);
            log.info(
                    "工单下达消息已发送: woId={}, woNo={}, tenantId={}",
                    entity.getWoId(),
                    entity.getWoNo(),
                    message.getTenantId());
        } catch (Exception e) {
            log.error("工单下达消息发送失败: woId={}", entity.getWoId(), e);
            /**
             * ERP 已提交；仅成功落库的消息记录可补偿。提交后回调前宕机或记录落库失败
             * 仍需 ERP 同库事务发件箱兜底，本方法不能保证该窗口内的消息可恢复。
             */
        }
    }

    /**
     * 解析工单所属租户 ID(供 MQ 消息携带)
     * <p>优先取工单自身 tenant_id(权威值), 回退当前线程租户上下文；均无法解析时返回 {@code null}，
     * 由消费端直接拒绝处理并显式失败，进入重试/死信，绝不静默落到系统租户 000000。</p>
     *
     * @param woTenantId 工单行上的 tenant_id
     * @return 租户 ID，无法解析时返回 {@code null}
     */
    private Long resolveMessageTenantId(String woTenantId) {
        String tenantId =
                StringUtils.isNotBlank(woTenantId) ? woTenantId : TenantHelper.getTenantId();
        if (StringUtils.isBlank(tenantId)) {
            log.error("工单租户信息缺失, MQ 消息将不带 tenantId(消费端将显式失败): tenantId={}", woTenantId);
            return null;
        }
        try {
            return Long.parseLong(tenantId);
        } catch (NumberFormatException e) {
            log.error("工单租户ID非法, MQ 消息将不带 tenantId: tenantId={}", tenantId);
            return null;
        }
    }

    /**
     * 获取工作流表单对应的业务数据
     *
     * @param dataId 业务数据 ID（工单 ID 字符串）
     * @return 工单详情
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Object getBusinessDataById(String dataId) {
        return selectById(Long.parseLong(dataId));
    }

    /**
     * 获取任务节点的流程变量（当前未自定义变量，返回 {@code null}）
     *
     * @param taskNameId 任务节点标识
     * @param values     流程变量
     * @return 任务变量映射，未定义时返回 {@code null}
     */
    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 获取任务节点的候选处理人（当前未配置，返回 {@code null}）
     *
     * @param taskNameId 任务节点标识
     * @param values     流程变量
     * @return 候选用户名列表，未配置时返回 {@code null}
     */
    @Override
    public List<String> flowCandidateUsernamesOfTask(
            String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 更新工单IPQC聚合质检状态
     *
     * @param woId          工单ID
     * @param qualityStatus 质检状态
     * @param ipqcId        IPQC检验单ID
     * @param ipqcCode      IPQC检验单号
     * @param inspectorId   检验员ID
     * @param inspectorName 检验员姓名
     * @param ipqcTime      检验时间
     * @return 影响行数
     */
    @Override
    public int updateIpqcStatus(
            Long woId,
            String qualityStatus,
            Long ipqcId,
            String ipqcCode,
            Long inspectorId,
            String inspectorName,
            Date ipqcTime) {
        return baseMapper.update(
                null,
                Wrappers.<PrdWorkOrder>lambdaUpdate()
                        .set(PrdWorkOrder::getQualityStatus, qualityStatus)
                        .set(PrdWorkOrder::getQualityCheckId, ipqcId)
                        .set(PrdWorkOrder::getQualityCheckNo, ipqcCode)
                        .set(PrdWorkOrder::getInspectorId, inspectorId)
                        .set(PrdWorkOrder::getInspectorName, inspectorName)
                        .set(PrdWorkOrder::getInspectTime, ipqcTime)
                        .eq(PrdWorkOrder::getWoId, woId));
    }

    /**
     * 批量填充工单 VO 中的部门信息（通过 workshop_id → bd_workshop.dept_id 反查）
     * <p>deptId/deptName 为非持久化字段，无需落库，仅在查询时即时填充</p>
     */
    private void fillDeptInfo(List<PrdWorkOrderVo> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        /**
         * 收集所有唯一的 workshopId
         */
        Map<Long, Long> workshopToDept = new HashMap<>();
        Map<Long, String> workshopToDeptName = new HashMap<>();
        for (PrdWorkOrderVo vo : vos) {
            if (vo.getWorkshopId() != null) {
                workshopToDept.putIfAbsent(vo.getWorkshopId(), null);
            }
        }
        if (workshopToDept.isEmpty()) {
            return;
        }
        /**
         * 批量查询 bd_workshop 的 dept_id/dept_name
         */
        List<BdWorkshop> workshops = bdWorkshopMapper.selectBatchIds(workshopToDept.keySet());
        for (BdWorkshop ws : workshops) {
            if (ws.getDeptId() != null) {
                workshopToDept.put(ws.getWorkshopId(), ws.getDeptId());
                workshopToDeptName.put(ws.getWorkshopId(), ws.getDeptName());
            }
        }
        /**
         * 回填 VO
         */
        for (PrdWorkOrderVo vo : vos) {
            if (vo.getWorkshopId() != null) {
                vo.setDeptId(workshopToDept.get(vo.getWorkshopId()));
                vo.setDeptName(workshopToDeptName.get(vo.getWorkshopId()));
            }
        }
    }

    private LambdaQueryWrapper<PrdWorkOrder> buildQueryWrapper(PrdWorkOrderBo bo) {
        LambdaQueryWrapper<PrdWorkOrder> lqw =
                Wrappers.<PrdWorkOrder>lambdaQuery()
                        .eq(
                                StringUtils.isNotBlank(bo.getWoNo()),
                                PrdWorkOrder::getWoNo,
                                bo.getWoNo())
                        .like(
                                StringUtils.isNotBlank(bo.getPlanName()),
                                PrdWorkOrder::getPlanName,
                                bo.getPlanName())
                        .eq(
                                StringUtils.isNotBlank(bo.getSourceType()),
                                PrdWorkOrder::getSourceType,
                                bo.getSourceType())
                        .eq(bo.getSourceId() != null, PrdWorkOrder::getSourceId, bo.getSourceId())
                        .eq(
                                StringUtils.isNotBlank(bo.getSourceNo()),
                                PrdWorkOrder::getSourceNo,
                                bo.getSourceNo())
                        .eq(
                                bo.getSourceLineId() != null,
                                PrdWorkOrder::getSourceLineId,
                                bo.getSourceLineId())
                        .eq(
                                bo.getMaterialId() != null,
                                PrdWorkOrder::getMaterialId,
                                bo.getMaterialId())
                        .like(
                                StringUtils.isNotBlank(bo.getMaterialName()),
                                PrdWorkOrder::getMaterialName,
                                bo.getMaterialName())
                        .eq(
                                StringUtils.isNotBlank(bo.getWoStatus()),
                                PrdWorkOrder::getWoStatus,
                                bo.getWoStatus())
                        .eq(
                                bo.getWorkshopId() != null,
                                PrdWorkOrder::getWorkshopId,
                                bo.getWorkshopId())
                        .like(
                                StringUtils.isNotBlank(bo.getWorkshopName()),
                                PrdWorkOrder::getWorkshopName,
                                bo.getWorkshopName())
                        .eq(
                                bo.getWorkstationId() != null,
                                PrdWorkOrder::getWorkstationId,
                                bo.getWorkstationId())
                        .like(
                                StringUtils.isNotBlank(bo.getWorkstationName()),
                                PrdWorkOrder::getWorkstationName,
                                bo.getWorkstationName())
                        .eq(
                                bo.getProductionLineId() != null,
                                PrdWorkOrder::getProductionLineId,
                                bo.getProductionLineId())
                        .eq(
                                bo.getResponsibleId() != null,
                                PrdWorkOrder::getResponsibleId,
                                bo.getResponsibleId())
                        .like(
                                StringUtils.isNotBlank(bo.getResponsibleName()),
                                PrdWorkOrder::getResponsibleName,
                                bo.getResponsibleName())
                        .eq(bo.getTeamId() != null, PrdWorkOrder::getTeamId, bo.getTeamId())
                        .like(
                                StringUtils.isNotBlank(bo.getTeamName()),
                                PrdWorkOrder::getTeamName,
                                bo.getTeamName())
                        .eq(bo.getPriority() != null, PrdWorkOrder::getPriority, bo.getPriority());
        return lqw;
    }

    private LambdaQueryWrapper<PrdWorkOrder> buildEntityQueryWrapper(PrdWorkOrder entity) {
        LambdaQueryWrapper<PrdWorkOrder> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(entity.getWoNo()), PrdWorkOrder::getWoNo, entity.getWoNo());
        lqw.like(
                StringUtils.isNotBlank(entity.getPlanName()),
                PrdWorkOrder::getPlanName,
                entity.getPlanName());
        lqw.eq(
                StringUtils.isNotBlank(entity.getSourceType()),
                PrdWorkOrder::getSourceType,
                entity.getSourceType());
        lqw.eq(entity.getSourceId() != null, PrdWorkOrder::getSourceId, entity.getSourceId());
        lqw.eq(
                StringUtils.isNotBlank(entity.getSourceNo()),
                PrdWorkOrder::getSourceNo,
                entity.getSourceNo());
        lqw.eq(
                entity.getSourceLineId() != null,
                PrdWorkOrder::getSourceLineId,
                entity.getSourceLineId());
        lqw.eq(entity.getMaterialId() != null, PrdWorkOrder::getMaterialId, entity.getMaterialId());
        lqw.like(
                StringUtils.isNotBlank(entity.getMaterialName()),
                PrdWorkOrder::getMaterialName,
                entity.getMaterialName());
        lqw.eq(
                StringUtils.isNotBlank(entity.getWoStatus()),
                PrdWorkOrder::getWoStatus,
                entity.getWoStatus());
        lqw.eq(entity.getWorkshopId() != null, PrdWorkOrder::getWorkshopId, entity.getWorkshopId());
        lqw.like(
                StringUtils.isNotBlank(entity.getWorkshopName()),
                PrdWorkOrder::getWorkshopName,
                entity.getWorkshopName());
        lqw.eq(
                entity.getWorkstationId() != null,
                PrdWorkOrder::getWorkstationId,
                entity.getWorkstationId());
        lqw.like(
                StringUtils.isNotBlank(entity.getWorkstationName()),
                PrdWorkOrder::getWorkstationName,
                entity.getWorkstationName());
        lqw.eq(
                entity.getProductionLineId() != null,
                PrdWorkOrder::getProductionLineId,
                entity.getProductionLineId());
        lqw.eq(
                entity.getResponsibleId() != null,
                PrdWorkOrder::getResponsibleId,
                entity.getResponsibleId());
        lqw.like(
                StringUtils.isNotBlank(entity.getResponsibleName()),
                PrdWorkOrder::getResponsibleName,
                entity.getResponsibleName());
        lqw.eq(entity.getTeamId() != null, PrdWorkOrder::getTeamId, entity.getTeamId());
        lqw.like(
                StringUtils.isNotBlank(entity.getTeamName()),
                PrdWorkOrder::getTeamName,
                entity.getTeamName());
        lqw.eq(entity.getPriority() != null, PrdWorkOrder::getPriority, entity.getPriority());
        return lqw;
    }
}
