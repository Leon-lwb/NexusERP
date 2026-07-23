package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.core.domain.R;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.mq.message.WorkOrderReleaseMessage;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.bridge.IMesErpBridgeService;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.common.mq.MesMqSendService;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.ProWorkOrderBomLine;
import com.nexus.mes.pro.domain.ProWorkOrderStep;
import com.nexus.mes.pro.domain.bo.ProWorkOrderBo;
import com.nexus.mes.pro.domain.vo.ProWorkOrderBomLineVo;
import com.nexus.mes.pro.domain.vo.ProWorkOrderStepVo;
import com.nexus.mes.pro.domain.vo.ProWorkOrderVo;
import com.nexus.mes.pro.mapper.ProWorkOrderBomLineMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderStepMapper;
import com.nexus.mes.pro.service.IMesMbomService;
import com.nexus.mes.pro.domain.vo.MesMbomVo;
import com.nexus.mes.pro.domain.vo.MesMbomLineVo;
import com.nexus.mes.pro.service.IProWorkOrderService;
import com.nexus.mes.qc.domain.QcIpqc;
import com.nexus.mes.qc.mapper.QcIpqcMapper;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MES工单 Service 业务层实现
 * <p>属于 MES 生产管理模块，实现 工单 的业务逻辑，操作 MES 数据源并桥接 ERP 主数据。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("proWorkOrderService")
public class ProWorkOrderServiceImpl implements IProWorkOrderService {

    private final ProWorkOrderMapper baseMapper;
    private final ProWorkOrderStepMapper stepMapper;
    private final ProWorkOrderBomLineMapper woBomLineMapper;
    private final IMesErpBridgeService mesErpBridgeService;
    private final IMesMbomService mesMbomService;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final MesMqSendService mesMqSendService;
    private final EqEquipmentMapper equipmentMapper;
    /**
     * IPQC Mapper：completeWo 门禁查关键工序检验结果。
     * 直调 Mapper 而非 IQcIpqcService，避免与 QcIpqcServiceImpl(依赖 IProWorkOrderService) 形成循环依赖；
     * 本类 @DS("mes")，completeWo 经代理走 mes 上下文，直调 mes 库 Mapper 安全。
     */
    private final QcIpqcMapper qcIpqcMapper;

    @Override
    /**
     * 查询
     * @param bo bo
     * @param pageQuery pageQuery
     * @return 分页结果
     */
    public TableDataInfo<ProWorkOrderVo> selectPageList(ProWorkOrderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ProWorkOrder> lqw = buildQueryWrapper(bo);
        Page<ProWorkOrderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    /**
     * 查询
     * @param bo bo
     * @return 结果列表
     */
    public List<ProWorkOrderVo> selectList(ProWorkOrderBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    /**
     * 查询
     * @param mesWoId MES工单ID
     * @return 返回视图对象
     */
    public ProWorkOrderVo selectById(Long mesWoId) {
        ProWorkOrderVo vo = baseMapper.selectVoById(mesWoId);
        if (vo != null) {
            vo.setSteps(selectStepsByWoId(mesWoId));
            // 加载MBOM快照明细
            vo.setBomLines(selectBomLinesByWoId(mesWoId));
            // 富集工艺路线信息(若routingId/bomId存在)
            enrichWorkOrderVo(vo);
        }
        return vo;
    }

    /**
     * 查询工单MBOM快照明细
     */
    private List<ProWorkOrderBomLineVo> selectBomLinesByWoId(Long mesWoId) {
        return woBomLineMapper.selectVoList(Wrappers.lambdaQuery(ProWorkOrderBomLine.class)
            .eq(ProWorkOrderBomLine::getMesWoId, mesWoId)
            .orderByAsc(ProWorkOrderBomLine::getSortOrder));
    }

    /**
     * 富集工单VO: 填充工艺路线编码/名称、BOM版本
     */
    private void enrichWorkOrderVo(ProWorkOrderVo vo) {
        if (vo.getRoutingId() != null) {
            Map<String, Object> routingHeader = mesErpBridgeService.getRoutingHeader(vo.getRoutingId());
            if (routingHeader != null) {
                vo.setRoutingCode((String) routingHeader.get("routing_code"));
                vo.setRoutingName((String) routingHeader.get("routing_name"));
            }
        }
        if (vo.getBomId() != null) {
            Map<String, Object> bomHeader = mesErpBridgeService.getBomHeader(vo.getBomId());
            if (bomHeader != null) {
                vo.setBomVersion((String) bomHeader.get("bom_version"));
            }
        }
        // P1-6: 填充关联设备编码/名称
        if (vo.getEquipmentId() != null) {
            EqEquipment eq = equipmentMapper.selectById(vo.getEquipmentId());
            if (eq != null) {
                vo.setEquipmentCode(eq.getEquipmentCode());
                vo.setEquipmentName(eq.getEquipmentName());
            }
        }
    }

    @Override
    /**
     * 查询
     * @param erpWoId ERP工单ID
     * @return 返回视图对象
     */
    public ProWorkOrderVo selectByErpWoId(Long erpWoId) {
        LambdaQueryWrapper<ProWorkOrder> lqw = Wrappers.<ProWorkOrder>lambdaQuery()
            .eq(ProWorkOrder::getErpWoId, erpWoId);
        return baseMapper.selectVoOne(lqw, false);
    }

    @Override
    /**
     * 查询
     * @param mesWoId MES工单ID
     * @return 结果列表
     */
    public List<ProWorkOrderStepVo> selectStepsByWoId(Long mesWoId) {
        LambdaQueryWrapper<ProWorkOrderStep> lqw = Wrappers.<ProWorkOrderStep>lambdaQuery()
            .eq(ProWorkOrderStep::getMesWoId, mesWoId)
            .orderByAsc(ProWorkOrderStep::getSequenceNum);
        return stepMapper.selectVoList(lqw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 新增
     * @param bo bo
     * @return 受影响行数
     */
    public int insertByBo(ProWorkOrderBo bo) {
        ProWorkOrder entity = MapstructUtils.convert(bo, ProWorkOrder.class);
        if (StringUtils.isBlank(entity.getWoStatus())) {
            entity.setWoStatus(STATUS_CREATED);
        }
        int rows = baseMapper.insert(entity);
        // 回写雪花ID到BO，供Controller返回查询
        bo.setMesWoId(entity.getMesWoId());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 更新
     * @param bo bo
     * @return 受影响行数
     */
    public int updateByBo(ProWorkOrderBo bo) {
        ProWorkOrder entity = MapstructUtils.convert(bo, ProWorkOrder.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除
     * @param ids 批量操作ID集合
     * @return 受影响行数
     */
    public int deleteByIds(Long[] ids) {
        // 仅新建或已取消状态的工单可删除
        List<ProWorkOrder> orders = baseMapper.selectBatchIds(List.of(ids));
        boolean hasNonDeletable = orders.stream()
            .anyMatch(o -> !STATUS_CREATED.equals(o.getWoStatus()) && !STATUS_CANCELLED.equals(o.getWoStatus()));
        if (hasNonDeletable) {
            throw new RuntimeException("仅新建或已取消状态的工单可删除");
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    /**
     * 下达
     * @param mesWoId MES工单ID
     */
    public void release(Long mesWoId) {
        // 先读取工单信息(在事务外,通过桥接服务读取ERP数据)
        ProWorkOrder wo = baseMapper.selectById(mesWoId);
        if (wo == null) {
            throw new RuntimeException("工单不存在: mesWoId=" + mesWoId);
        }
        if (!STATUS_CREATED.equals(wo.getWoStatus())) {
            throw new RuntimeException("仅新建状态工单可下达");
        }
        // 事务外桥接读取ERP数据(MES来源工单的路由步骤+MBOM)
        List<Map<String, Object>> routingSteps = null;
        if (wo.getRoutingId() != null) {
            routingSteps = mesErpBridgeService.getRoutingSteps(wo.getRoutingId());
        }
        MesMbomVo mbom = null;
        if (wo.getBomId() != null) {
            mbom = mesMbomService.selectByBomId(wo.getBomId());
        }
        // 事务内: CAS状态更新 + 幂等快照步骤/BOM
        doReleaseAndSnapshot(mesWoId, wo, routingSteps, mbom);
    }

    /**
     * 事务内下达 + 幂等快照(ERP来源工单可能已在createFromErpMessage时快照,此处幂等保护)
     */
    @Transactional(rollbackFor = Exception.class)
    public void doReleaseAndSnapshot(Long mesWoId, ProWorkOrder wo,
                                     List<Map<String, Object>> routingSteps, MesMbomVo mbom) {
        // CAS更新状态
        int rows = baseMapper.update(null, Wrappers.<ProWorkOrder>lambdaUpdate()
            .set(ProWorkOrder::getWoStatus, STATUS_RELEASED)
            .eq(ProWorkOrder::getMesWoId, mesWoId)
            .eq(ProWorkOrder::getWoStatus, STATUS_CREATED));
        if (rows == 0) {
            throw new RuntimeException("工单状态已变更或不存在，仅新建状态工单可下达");
        }
        // 幂等快照步骤(若尚未生成)
        Long stepCount = stepMapper.selectCount(Wrappers.lambdaQuery(ProWorkOrderStep.class)
            .eq(ProWorkOrderStep::getMesWoId, mesWoId));
        if ((stepCount == null || stepCount == 0) && routingSteps != null && !routingSteps.isEmpty()) {
            generateStepsFromRouting(wo, routingSteps);
        }
        // 幂等快照MBOM(若尚未生成)
        Long bomLineCount = woBomLineMapper.selectCount(Wrappers.lambdaQuery(ProWorkOrderBomLine.class)
            .eq(ProWorkOrderBomLine::getMesWoId, mesWoId));
        if ((bomLineCount == null || bomLineCount == 0) && mbom != null && mbom.getLines() != null && !mbom.getLines().isEmpty()) {
            snapshotBomLines(wo, mbom);
        }
        log.info("MES工单下达成功: mesWoId={}", mesWoId);
    }

    /**
     * 消费ERP工单下达消息，创建MES工单及工序
     * <p>
     * 架构设计：桥接查询（跨erp数据源）在事务外执行，DB写入在事务内执行，
     * 避免 @Transactional 绑定 mes 数据源后桥接查询无法切换到 erp 的问题。
     */
    @Override
    public void createFromErpMessage(WorkOrderReleaseMessage message) {
        log.info("开始创建MES工单: erpWoId={}, woNo={}", message.getWoId(), message.getWoNo());

        // 1. 幂等校验：通过erpWoId查重
        ProWorkOrderVo existing = selectByErpWoId(message.getWoId());
        if (existing != null) {
            log.info("MES工单已存在,跳过创建: erpWoId={}, mesWoId={}", message.getWoId(), existing.getMesWoId());
            return;
        }

        // 2. 【事务外】桥接查询所有ERP主数据（只读，跨erp数据源）
        Long unitId = null;
        String unitName = null;
        if (message.getMaterialId() != null) {
            Map<String, Object> material = mesErpBridgeService.getMaterial(message.getMaterialId());
            if (material != null) {
                unitId = toLong(material.get("base_uom_id"));
                if (unitId != null) {
                    unitName = mesErpBridgeService.getUnitName(unitId);
                }
            }
        }

        String workshopName = null;
        if (message.getWorkshopId() != null) {
            Map<String, Object> workshop = mesErpBridgeService.getWorkshop(message.getWorkshopId());
            if (workshop != null) {
                workshopName = (String) workshop.get("workshop_name");
            }
        }

        Long routingId = message.getRoutingId();
        if (routingId == null && message.getMaterialId() != null) {
            routingId = mesErpBridgeService.getDefaultRoutingId(message.getMaterialId());
            log.info("通过桥接服务查询默认工艺路线: materialId={}, routingId={}", message.getMaterialId(), routingId);
        }

        List<Map<String, Object>> routingSteps = null;
        if (routingId != null) {
            routingSteps = mesErpBridgeService.getRoutingSteps(routingId);
        }

        // 读取MBOM明细(若BOM已编制MBOM)
        MesMbomVo mbom = null;
        if (message.getBomId() != null) {
            mbom = mesMbomService.selectByBomId(message.getBomId());
        }

        // 3. 【事务内】创建MES工单及工序及BOM快照（仅写mes库）
        doCreateWorkOrderAndSteps(message, unitId, unitName, workshopName, routingId, routingSteps, mbom);
    }

    /**
     * 事务内创建工单及工序（仅操作mes数据源）
     */
    @Transactional(rollbackFor = Exception.class)
    public void doCreateWorkOrderAndSteps(WorkOrderReleaseMessage message,
                                          Long unitId, String unitName,
                                          String workshopName, Long routingId,
                                          List<Map<String, Object>> routingSteps,
                                          MesMbomVo mbom) {
        // 创建MES工单主记录
        ProWorkOrder workOrder = new ProWorkOrder();
        workOrder.setErpWoId(message.getWoId());
        workOrder.setWoNo(message.getWoNo());
        workOrder.setMaterialId(message.getMaterialId());
        workOrder.setMaterialCode(message.getMaterialCode());
        workOrder.setMaterialName(message.getMaterialName());
        workOrder.setPlannedQty(message.getPlannedQty());
        workOrder.setUnitId(unitId);
        workOrder.setUnitName(unitName);
        workOrder.setPlanStartDate(message.getPlannedStartDate());
        workOrder.setPlanEndDate(message.getPlannedEndDate());
        workOrder.setWorkshopId(message.getWorkshopId());
        workOrder.setWorkshopName(workshopName);
        workOrder.setBomId(message.getBomId());
        workOrder.setRoutingId(routingId);
        workOrder.setWoStatus(STATUS_CREATED);
        workOrder.setPriority(5);
        baseMapper.insert(workOrder);
        log.info("MES工单创建成功: mesWoId={}, woNo={}", workOrder.getMesWoId(), workOrder.getWoNo());

        // 按工艺路线步骤生成工序
        if (routingSteps != null && !routingSteps.isEmpty()) {
            generateStepsFromRouting(workOrder, routingSteps);
        } else {
            log.warn("未找到工艺路线或无步骤,跳过工序生成: woNo={}, routingId={}", message.getWoNo(), routingId);
        }

        // MBOM快照: 将MBOM明细快照到 pro_work_order_bom_line(隔离后续MBOM/ERP变更)
        if (mbom != null && mbom.getLines() != null && !mbom.getLines().isEmpty()) {
            snapshotBomLines(workOrder, mbom);
        }

        log.info("MES工单及工序创建完成: mesWoId={}, woNo={}", workOrder.getMesWoId(), workOrder.getWoNo());
    }

    /**
     * 按ERP工艺路线步骤生成MES工单工序（事务内调用，routingSteps已预加载）
     */
    private void generateStepsFromRouting(ProWorkOrder workOrder, List<Map<String, Object>> routingSteps) {
        List<ProWorkOrderStep> steps = new ArrayList<>();
        for (Map<String, Object> rs : routingSteps) {
            ProWorkOrderStep step = new ProWorkOrderStep();
            step.setMesWoId(workOrder.getMesWoId());
            step.setSequenceNum(toInt(rs.get("sequence_num")));
            step.setProcessId(toLong(rs.get("process_id")));
            step.setProcessCode((String) rs.get("process_code"));
            step.setProcessName((String) rs.get("process_name"));
            step.setWorkcenterId(toLong(rs.get("workstation_id")));
            step.setWorkcenterCode((String) rs.get("workstation_code"));
            step.setPlannedQty(workOrder.getPlannedQty());
            step.setStepStatus(STEP_PENDING);
            step.setIsKeyProcess(toInt(rs.get("is_key_process")));
            steps.add(step);
        }

        // 批量插入工序步骤
        for (ProWorkOrderStep step : steps) {
            stepMapper.insert(step);
        }
        log.info("工序步骤生成完成: mesWoId={}, stepCount={}", workOrder.getMesWoId(), steps.size());
    }

    /**
     * 快照MBOM明细到工单BOM快照表(事务内调用, MBOM行已含富集信息)
     */
    private void snapshotBomLines(ProWorkOrder workOrder, MesMbomVo mbom) {
        List<MesMbomLineVo> lines = mbom.getLines();
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<ProWorkOrderBomLine> snapshots = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            MesMbomLineVo line = lines.get(i);
            ProWorkOrderBomLine snapshot = new ProWorkOrderBomLine();
            snapshot.setMesWoId(workOrder.getMesWoId());
            snapshot.setMbomLineId(line.getMbomLineId());
            snapshot.setBomId(mbom.getBomId());
            snapshot.setBomDetailId(line.getBomDetailId());
            snapshot.setChildMaterialId(line.getChildMaterialId());
            snapshot.setChildMaterialCode(line.getChildMaterialCode());
            snapshot.setChildMaterialName(line.getChildMaterialName());
            snapshot.setQuantityPerParent(line.getQuantityPerParent());
            snapshot.setLossRate(line.getLossRate() != null ? line.getLossRate() : BigDecimal.ZERO);
            // 计算含损耗总用量
            BigDecimal qty = line.getQuantityPerParent() != null ? line.getQuantityPerParent() : BigDecimal.ZERO;
            BigDecimal loss = line.getLossRate() != null ? line.getLossRate() : BigDecimal.ZERO;
            snapshot.setTotalQuantity(qty.multiply(BigDecimal.ONE.add(loss.divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP))));
            snapshot.setRoutingStepId(line.getRoutingStepId());
            snapshot.setSequenceNum(line.getRoutingStepSequence());
            snapshot.setProcessId(line.getProcessId());
            snapshot.setProcessCode(line.getProcessCode());
            snapshot.setProcessName(line.getProcessName());
            snapshot.setWorkstationId(line.getWorkstationId());
            snapshot.setWorkstationCode(line.getWorkstationCode());
            snapshot.setWorkstationName(line.getWorkstationName());
            snapshot.setOperationContent(line.getOperationContent());
            snapshot.setBackflushFlag(line.getBackflushFlag());
            snapshot.setSortOrder(line.getSortOrder() != null ? line.getSortOrder() : i + 1);
            snapshot.setRemark(line.getRemark());
            snapshots.add(snapshot);
        }
        for (ProWorkOrderBomLine snapshot : snapshots) {
            woBomLineMapper.insert(snapshot);
        }
        log.info("MBOM快照生成完成: mesWoId={}, bomLineCount={}", workOrder.getMesWoId(), snapshots.size());
    }

    // ==================== Flowable 审批流方法 ====================

    /**
     * 提交审批(CREATED -> APPROVING)
     * <p>
     * 注意：不加 @Transactional，避免持有 mes 连接覆盖整个 Flowable 启动过程。
     * 仅有一个 selectById 只读查询，无需事务。
     */
    @Override
    public boolean submit(String mesWoIdStr, String selectFlowId) {
        Long mesWoId = Long.parseLong(mesWoIdStr);
        ProWorkOrder entity = baseMapper.selectById(mesWoId);
        if (entity == null) {
            throw new RuntimeException("MES工单不存在");
        }
        if (!STATUS_CREATED.equals(entity.getWoStatus())) {
            throw new RuntimeException("只有新建状态的工单才能提交审批");
        }
        // 解析selectFlowId
        if (StringUtils.isBlank(selectFlowId)) {
            if (entity.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(entity.getSelectFlowId());
            } else {
                throw new RuntimeException("请先在编辑页选择审批流程并保存");
            }
        }
        // 构建流程变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", mesWoIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("mesWoId", entity.getMesWoId());
        variables.put("woNo", entity.getWoNo());
        // 调用流程服务（processService 自身 @DS("master") + REQUIRES_NEW，会自行切换数据源）
        R<Void> result = processService.startProcessByDataId(
            mesWoIdStr, selectFlowId, "proWorkOrderService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new RuntimeException(result.getMsg());
    }

    /**
     * 查询流程实例ID
     * <p>
     * 必须加 @DS("master")：Flowable 表在 master 库，
     * 类级 @DS("mes") 会导致查询 mes 库报错。
     */
    @Override
    @DS("master")
    public String getProcInsId(String mesWoIdStr) {
        // 策略1: 查桥接表（WfMyBusinessServiceImpl 自身 @DS("master")）
        try {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(mesWoIdStr);
            if (business != null && StringUtils.isNotBlank(business.getProcessInstanceId())) {
                return business.getProcessInstanceId();
            }
        } catch (Exception e) {
            log.warn("getByDataId 查询失败: dataId={}", mesWoIdStr, e);
        }
        // 策略2: 查Flowable运行时
        ProcessInstance runtime = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(mesWoIdStr).singleResult();
        if (runtime != null) {
            return runtime.getId();
        }
        // 策略3: 查Flowable历史
        HistoricProcessInstance hist = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(mesWoIdStr).singleResult();
        return hist != null ? hist.getId() : null;
    }

    /**
     * Flowable 审批回调
     * <p>
     * 显式声明 @DS("mes") 确保写入 mes 库，不依赖类级注解的 AOP 顺序。
     * REQUIRES_NEW 隔离事务，避免 Flowable master 事务回滚影响 mes 写入。
     */
    @Override
    @DS("mes")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void afterFlowHandle(WfMyBusiness business) {
        Long mesWoId = Long.parseLong(business.getDataId());
        ProWorkOrder entity = baseMapper.selectById(mesWoId);
        if (entity == null) {
            return;
        }
        String actStatus = business.getActStatus();
        boolean changed = false;
        if (ActStatus.doing.equals(actStatus)) {
            entity.setWoStatus(STATUS_APPROVING);
            changed = true;
        } else if (ActStatus.pass.equals(actStatus)) {
            entity.setWoStatus(STATUS_RELEASED);
            changed = true;
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)
            || ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus)
            || ActStatus.recall.equals(actStatus) || ActStatus.err.equals(actStatus)) {
            entity.setWoStatus(STATUS_CREATED);
            changed = true;
        }
        // delegate/assign/start 不改变状态，跳过更新
        if (changed) {
            baseMapper.updateById(entity);
            log.info("MES工单审批回调: mesWoId={}, actStatus={}, newWoStatus={}", mesWoId, actStatus, entity.getWoStatus());
        }
    }

    @Override
    /**
     * 获取
     * @param dataId ID
     * @return 返回结果
     */
    public Object getBusinessDataById(String dataId) {
        return selectById(Long.parseLong(dataId));
    }

    @Override
    /**
     * flowValuesOfTask 操作
     * @param taskNameId ID
     * @param Map<String Map<String
     * @param values values
     * @return 返回结果映射
     */
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    @Override
    /**
     * flowCandidateUsernamesOfTask 操作
     * @param taskNameId ID
     * @param Map<String Map<String
     * @param values values
     * @return 结果列表
     */
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 工单完工(RELEASED/IN_PROGRESS -> COMPLETED) + MQ通知ERP
     * <p>
     * 由流转卡完工聚合触发。CAS更新状态，事务提交后异步发送MQ消息。
     * 兼容工单从未进入 IN_PROGRESS 的场景(如流转卡在未开工状态下被直接完工):
     * 此时一并补写 actualStartDate，避免完工工单缺失开工时间。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeWo(Long mesWoId) {
        ProWorkOrder wo = baseMapper.selectById(mesWoId);
        if (wo == null) {
            log.warn("MES工单不存在，跳过完工: mesWoId={}", mesWoId);
            return;
        }
        if (!STATUS_IN_PROGRESS.equals(wo.getWoStatus()) && !STATUS_RELEASED.equals(wo.getWoStatus())) {
            log.warn("工单状态不满足完工条件，跳过: mesWoId={}, woStatus={}", mesWoId, wo.getWoStatus());
            return;
        }

        // IPQC 门禁：关键工序(isKeyProcess=1)必须有 QUALIFIED 的 IPQC 才允许工单完工
        List<ProWorkOrderStep> keySteps = stepMapper.selectList(Wrappers.<ProWorkOrderStep>lambdaQuery()
            .eq(ProWorkOrderStep::getMesWoId, mesWoId)
            .eq(ProWorkOrderStep::getIsKeyProcess, 1));
        if (keySteps != null && !keySteps.isEmpty()) {
            List<QcIpqc> ipqcList = qcIpqcMapper.selectList(Wrappers.<QcIpqc>lambdaQuery()
                .eq(QcIpqc::getMesWoId, mesWoId));
            Map<Long, String> resultByProcess = new HashMap<>();
            if (ipqcList != null) {
                for (QcIpqc ipqc : ipqcList) {
                    resultByProcess.putIfAbsent(ipqc.getProcessId(), ipqc.getInspectResult());
                }
            }
            StringBuilder pending = new StringBuilder();
            StringBuilder unqualified = new StringBuilder();
            for (ProWorkOrderStep ks : keySteps) {
                String result = resultByProcess.get(ks.getProcessId());
                if ("QUALIFIED".equals(result)) {
                    continue;
                }
                if ("UNQUALIFIED".equals(result)) {
                    if (unqualified.length() > 0) {
                        unqualified.append("、");
                    }
                    unqualified.append(ks.getProcessName());
                } else {
                    if (pending.length() > 0) {
                        pending.append("、");
                    }
                    pending.append(ks.getProcessName());
                }
            }
            if (pending.length() > 0) {
                throw new RuntimeException("以下关键工序尚未完成IPQC过程检验，禁止工单完工：" + pending);
            }
            if (unqualified.length() > 0) {
                throw new RuntimeException("以下关键工序IPQC检验不合格，禁止工单完工：" + unqualified);
            }
        }

        // CAS更新 RELEASED/IN_PROGRESS -> COMPLETED; 若工单从未开工(actualStartDate为空)则补写开工时间
        Date now = new Date();
        int rows = baseMapper.update(null, Wrappers.<ProWorkOrder>lambdaUpdate()
            .set(ProWorkOrder::getWoStatus, STATUS_COMPLETED)
            .set(ProWorkOrder::getActualEndDate, now)
            .set(wo.getActualStartDate() == null, ProWorkOrder::getActualStartDate, now)
            .eq(ProWorkOrder::getMesWoId, mesWoId)
            .in(ProWorkOrder::getWoStatus, STATUS_RELEASED, STATUS_IN_PROGRESS));
        if (rows == 0) {
            log.warn("工单状态并发变更，跳过完工: mesWoId={}", mesWoId);
            return;
        }

        log.info("MES工单完工: mesWoId={}, woNo={}, completedQty={}, scrapQty={}",
            mesWoId, wo.getWoNo(), wo.getCompletedQty(), wo.getScrapQty());

        // 事务提交后异步发送MQ消息通知ERP(completed_qty/scrap_qty 已由 completeCard 在本事务内聚合)
        final BigDecimal completedQty = wo.getCompletedQty() != null ? wo.getCompletedQty() : BigDecimal.ZERO;
        final BigDecimal scrappedQty = wo.getScrapQty() != null ? wo.getScrapQty() : BigDecimal.ZERO;
        final String woNo = wo.getWoNo();
        final Long erpWoId = wo.getErpWoId();
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                /**
                 * afterCommit 操作
                 */
                public void afterCommit() {
                    String tenantId = TenantHelper.getTenantId();
                    mesMqSendService.sendWorkOrderCompleted(
                        mesWoId, erpWoId, woNo, completedQty, scrappedQty, tenantId);
                }
            });
    }

    // ==================== 私有辅助方法 ====================

    private LambdaQueryWrapper<ProWorkOrder> buildQueryWrapper(ProWorkOrderBo bo) {
        LambdaQueryWrapper<ProWorkOrder> lqw = Wrappers.<ProWorkOrder>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getWoNo()), ProWorkOrder::getWoNo, bo.getWoNo())
            .eq(bo.getErpWoId() != null, ProWorkOrder::getErpWoId, bo.getErpWoId())
            .eq(bo.getMaterialId() != null, ProWorkOrder::getMaterialId, bo.getMaterialId())
            .like(StringUtils.isNotBlank(bo.getMaterialName()), ProWorkOrder::getMaterialName, bo.getMaterialName())
            .eq(bo.getWorkshopId() != null, ProWorkOrder::getWorkshopId, bo.getWorkshopId())
            .eq(StringUtils.isNotBlank(bo.getWoStatus()), ProWorkOrder::getWoStatus, bo.getWoStatus())
            .orderByDesc(ProWorkOrder::getCreateTime);
        return lqw;
    }

    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInt(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
