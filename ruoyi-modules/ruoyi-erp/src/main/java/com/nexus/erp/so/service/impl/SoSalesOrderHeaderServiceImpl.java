package com.nexus.erp.so.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdBom;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdBomMapper;
import com.nexus.erp.prd.domain.PrdProductionPlan;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdProductionPlanMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.domain.bo.SoSalesOrderHeaderBo;
import com.nexus.erp.so.domain.bo.SoSalesOrderLineBo;
import com.nexus.erp.so.domain.vo.SoSalesOrderHeaderVo;
import com.nexus.erp.so.domain.vo.SoSalesOrderLineVo;
import com.nexus.erp.so.event.SalesOrderFulfilledEvent;
import com.nexus.erp.so.mapper.SoSalesOrderHeaderMapper;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ICreditCheckService;
import com.nexus.erp.so.service.ISoSalesOrderHeaderService;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IWmStockReservationService;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 销售订单Service业务层处理
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("soSalesOrderHeaderService")
public class SoSalesOrderHeaderServiceImpl implements ISoSalesOrderHeaderService {

    private final SoSalesOrderHeaderMapper baseMapper;
    private final SoSalesOrderLineMapper soSalesOrderLineMapper;
    private final BdMaterialMapper bdMaterialMapper;
    private final BdBomMapper bdBomMapper;
    private final PrdProductionPlanMapper prdProductionPlanMapper;
    private final PrdWorkOrderMapper prdWorkOrderMapper;
    private final WmWarehouseMapper wmWarehouseMapper;
    private final AutoCodeUtil autoCodeUtil;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final FlowableProcInsIdHelper procInsIdHelper;
    private final ICreditCheckService creditCheckService;
    private final IWmStockReservationService stockReservationService;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 分页查询销售订单列表
     */
    @Override
    public TableDataInfo<SoSalesOrderHeaderVo> selectPageList(SoSalesOrderHeaderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoSalesOrderHeader> lqw = buildQueryWrapper(bo);
        Page<SoSalesOrderHeaderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询销售订单列表
     */
    @Override
    public List<SoSalesOrderHeaderVo> selectList(SoSalesOrderHeaderBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询销售订单详情（包含行明细及关联生产计划）
     */
    @Override
    public SoSalesOrderHeaderVo selectById(Long id) {
        SoSalesOrderHeaderVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            List<SoSalesOrderLine> lineList = soSalesOrderLineMapper.selectList(
                new LambdaQueryWrapper<SoSalesOrderLine>()
                    .eq(SoSalesOrderLine::getSoId, id));
            List<SoSalesOrderLineVo> lineVoList = MapstructUtils.convert(lineList, SoSalesOrderLineVo.class);
            // 批量回填关联展示信息：生产计划号、生产工单号(经计划链派生)、仓库名称
            populateLineLinkage(lineVoList);
            vo.setSoSalesOrderLineList(lineVoList);
        }
        return vo;
    }

    /**
     * 批量回填销售订单行的关联展示信息：关联生产计划号、关联生产工单号(经计划链派生)、仓库名称。
     * <p>批量查询避免 N+1：一次查计划、一次查工单、一次查仓库，内存映射。
     * 关联工单经 plan.orderLineId -> plan.planId -> PrdWorkOrder 链路派生；
     * 一个 SO 行可能对应多张工单，工单号逗号拼接，首张工单 ID 写入 linkedMoId 供前端跳转。
     */
    private void populateLineLinkage(List<SoSalesOrderLineVo> lineVoList) {
        if (lineVoList == null || lineVoList.isEmpty()) {
            return;
        }
        List<Long> lineIds = lineVoList.stream()
            .map(SoSalesOrderLineVo::getLineId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        if (lineIds.isEmpty()) {
            resolveWarehouseNames(lineVoList);
            return;
        }
        // 1. 批量查询关联生产计划: orderLineId IN (lineIds)
        List<PrdProductionPlan> plans = prdProductionPlanMapper.selectList(
            new LambdaQueryWrapper<PrdProductionPlan>()
                .in(PrdProductionPlan::getOrderLineId, lineIds));
        Map<Long, PrdProductionPlan> lineIdToPlan = new HashMap<>();
        for (PrdProductionPlan p : plans) {
            if (p.getOrderLineId() != null && !lineIdToPlan.containsKey(p.getOrderLineId())) {
                lineIdToPlan.put(p.getOrderLineId(), p);
            }
        }

        // 2. 批量查询计划下工单: planId IN (planIds)，按 planId 分组
        List<Long> planIds = plans.stream()
            .map(PrdProductionPlan::getPlanId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, List<PrdWorkOrder>> planIdToWorkOrders = new HashMap<>();
        if (!planIds.isEmpty()) {
            List<PrdWorkOrder> workOrders = prdWorkOrderMapper.selectList(
                new LambdaQueryWrapper<PrdWorkOrder>()
                    .in(PrdWorkOrder::getPlanId, planIds));
            for (PrdWorkOrder w : workOrders) {
                if (w.getPlanId() != null) {
                    planIdToWorkOrders.computeIfAbsent(w.getPlanId(), k -> new ArrayList<>()).add(w);
                }
            }
        }

        // 3. 逐行回填计划号 + 工单号
        for (SoSalesOrderLineVo lineVo : lineVoList) {
            if (lineVo.getLineId() == null) {
                continue;
            }
            PrdProductionPlan plan = lineIdToPlan.get(lineVo.getLineId());
            if (plan == null) {
                continue;
            }
            lineVo.setLinkedPlanNo(plan.getPlanNo());
            lineVo.setLinkedPlanId(plan.getPlanId());
            List<PrdWorkOrder> wos = planIdToWorkOrders.get(plan.getPlanId());
            if (wos != null && !wos.isEmpty()) {
                String woNoJoined = wos.stream()
                    .map(PrdWorkOrder::getWoNo)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.joining(","));
                lineVo.setLinkedWoNo(woNoJoined);
                // 首张工单ID供前端跳转(多工单时跳首张); 用独立 VO 字段 linkedWoId，避免污染落库的 linkedMoId
                lineVo.setLinkedWoId(wos.get(0).getWoId());
            }
        }

        // 4. 批量解析仓库名
        resolveWarehouseNames(lineVoList);
    }

    /**
     * 批量回填仓库名称: warehouseId IN (line.warehouseIds) -> warehouseName
     */
    private void resolveWarehouseNames(List<SoSalesOrderLineVo> lineVoList) {
        List<Long> warehouseIds = lineVoList.stream()
            .map(SoSalesOrderLineVo::getWarehouseId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        if (warehouseIds.isEmpty()) {
            return;
        }
        List<WmWarehouse> warehouses = wmWarehouseMapper.selectList(
            new LambdaQueryWrapper<WmWarehouse>()
                .in(WmWarehouse::getWarehouseId, warehouseIds));
        Map<Long, String> idToName = new HashMap<>();
        for (WmWarehouse w : warehouses) {
            if (w.getWarehouseId() != null) {
                idToName.put(w.getWarehouseId(), w.getWarehouseName());
            }
        }
        for (SoSalesOrderLineVo lineVo : lineVoList) {
            if (lineVo.getWarehouseId() != null) {
                lineVo.setWarehouseName(idToName.get(lineVo.getWarehouseId()));
            }
        }
    }

    /**
     * 新增销售订单（默认草稿状态并保存行明细）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoSalesOrderHeaderBo bo) {
        SoSalesOrderHeader entity = MapstructUtils.convert(bo, SoSalesOrderHeader.class);
        if (StringUtils.isBlank(entity.getOrderStatus())) {
            entity.setOrderStatus(STATUS_DRAFT);
        }
        if (StringUtils.isBlank(entity.getSoNo())) {
            entity.setSoNo(autoCodeUtil.genSerialCode("SO_ORDER_CODE", null));
        }
        int rows = baseMapper.insert(entity);
        insertOrUpdateLines(entity.getSoId(), bo.getSoSalesOrderLineList());
        return rows;
    }

    /**
     * 修改销售订单（先删除原行明细再重新插入）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoSalesOrderHeaderBo bo) {
        SoSalesOrderHeader entity = MapstructUtils.convert(bo, SoSalesOrderHeader.class);
        soSalesOrderLineMapper.delete(new LambdaQueryWrapper<SoSalesOrderLine>()
            .eq(SoSalesOrderLine::getSoId, entity.getSoId()));
        insertOrUpdateLines(entity.getSoId(), bo.getSoSalesOrderLineList());
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除销售订单（同时删除行明细）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        soSalesOrderLineMapper.delete(new LambdaQueryWrapper<SoSalesOrderLine>()
            .in(SoSalesOrderLine::getSoId, List.of(ids)));
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 根据销售订单编号查询行明细
     */
    @Override
    public List<SoSalesOrderLineVo> selectLineListBySoNo(String soNo) {
        LambdaQueryWrapper<SoSalesOrderHeader> headerWrapper = Wrappers.lambdaQuery();
        headerWrapper.eq(SoSalesOrderHeader::getSoNo, soNo);
        SoSalesOrderHeader header = baseMapper.selectOne(headerWrapper);
        if (header == null) {
            return List.of();
        }
        LambdaQueryWrapper<SoSalesOrderLine> lineWrapper = Wrappers.lambdaQuery();
        lineWrapper.eq(SoSalesOrderLine::getSoId, header.getSoId());
        lineWrapper.orderByDesc(SoSalesOrderLine::getSortOrder);
        List<SoSalesOrderLine> lineList = soSalesOrderLineMapper.selectList(lineWrapper);
        return MapstructUtils.convert(lineList, SoSalesOrderLineVo.class);
    }

    /**
     * 确认销售订单（包含信用检查，通过后自动创建生产计划）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long soId) {
        SoSalesOrderHeader header = baseMapper.selectById(soId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        if (!STATUS_DRAFT.equals(header.getOrderStatus()) && !STATUS_APPROVED.equals(header.getOrderStatus())) {
            throw new RuntimeException("当前状态不允许确认");
        }

        // 信用检查与冻结
        creditCheckService.checkAndFreeze(header.getClientId(), soId,
            header.getTotalAmount() != null ? header.getTotalAmount() : BigDecimal.ZERO);
        header.setCreditStatus("PASSED");
        header.setCreditCheckTime(new Date());

        // 条件更新抢占 DRAFT/APPROVED → CONFIRMED, 防并发双确认(读-改-写窗口已消除)
        header.setOrderStatus(STATUS_CONFIRMED);
        header.setConfirmedTime(new Date());
        int updated = baseMapper.update(null,
            Wrappers.<SoSalesOrderHeader>lambdaUpdate()
                .set(SoSalesOrderHeader::getOrderStatus, STATUS_CONFIRMED)
                .set(SoSalesOrderHeader::getConfirmedTime, header.getConfirmedTime())
                .set(SoSalesOrderHeader::getCreditStatus, header.getCreditStatus())
                .set(SoSalesOrderHeader::getCreditCheckTime, header.getCreditCheckTime())
                .eq(SoSalesOrderHeader::getSoId, soId)
                .in(SoSalesOrderHeader::getOrderStatus, STATUS_DRAFT, STATUS_APPROVED));
        if (updated == 0) {
            throw new RuntimeException("订单状态已被并发操作变更，请刷新后重试");
        }
        log.info("销售订单确认成功, soId={}, soNo={}, creditStatus=PASSED", soId, header.getSoNo());

        // 库存预留: 对库存物料(非自制)触发预留
        reserveStockForSalesOrder(soId);

        // 自动创建生产计划（针对自制物料）
        createProductionPlansFromSalesOrder(soId, header.getSoNo());
    }

    /**
     * 履行销售订单（不强制，等价于 fulfill(soId, false)）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void fulfill(Long soId) {
        fulfill(soId, false);
    }

    /**
     * 履行销售订单
     * <p>严格门禁 + 收尾联动：
     * <ol>
     *   <li>校验所有行 delivered_qty >= ordered_qty（force=true 可跳过）</li>
     *   <li>清理残余库存预留（兜底，正常出库已消耗至 0）</li>
     *   <li>行状态→FULFILLED，fulfilled_qty 兜齐 ordered_qty（消除尾差）</li>
     *   <li>自制行关联的生产计划→CLOSED</li>
     *   <li>信用 FROZEN→USED（已交付形成应收，仍占额度，待 close 释放）</li>
     *   <li>头状态→FULFILLED + fulfilledTime</li>
     *   <li>发布 SalesOrderFulfilledEvent（供开票/通知等订阅）</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void fulfill(Long soId, boolean force) {
        SoSalesOrderHeader header = baseMapper.selectById(soId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        if (!STATUS_CONFIRMED.equals(header.getOrderStatus())) {
            throw new RuntimeException("只有已确认状态的订单才能履行");
        }

        List<SoSalesOrderLine> lines = soSalesOrderLineMapper.selectList(
            new LambdaQueryWrapper<SoSalesOrderLine>().eq(SoSalesOrderLine::getSoId, soId));
        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("订单无明细，无法履行");
        }

        // ① 严格门禁：所有行 delivered_qty >= ordered_qty
        BigDecimal tolerance = new BigDecimal("0.0001");
        List<String> shortLines = new ArrayList<>();
        for (SoSalesOrderLine line : lines) {
            BigDecimal need = line.getOrderedQty() == null ? BigDecimal.ZERO : line.getOrderedQty();
            BigDecimal done = line.getDeliveredQty() == null ? BigDecimal.ZERO : line.getDeliveredQty();
            if (need.subtract(done).compareTo(tolerance) > 0) {
                shortLines.add("行" + line.getLineNo() + "(" + line.getMaterialName()
                    + "):需" + need + "已发" + done);
            }
        }
        if (!shortLines.isEmpty() && !force) {
            throw new RuntimeException("存在未完全发货的行，无法履行：\n" + String.join("\n", shortLines)
                + "\n如需强制履行，请确认后重试。");
        }

        // ①' OQC硬门禁：任一行 OQC 不合格则禁止履行(质量门禁，force 不可绕过)
        List<String> oqcFailedLines = new ArrayList<>();
        for (SoSalesOrderLine line : lines) {
            if ("UNQUALIFIED".equals(line.getOqcStatus())) {
                oqcFailedLines.add("行" + line.getLineNo() + "(" + line.getMaterialName() + "):"
                    + line.getOqcCode());
            }
        }
        if (!oqcFailedLines.isEmpty()) {
            throw new RuntimeException("存在OQC检验不合格的销售订单行，禁止履行：\n"
                + String.join("\n", oqcFailedLines) + "\n请先处理不合格品后再履行。");
        }

        // ② 残余预留清理（兜底：正常出库已把 reserved_qty 消耗至 0）
        stockReservationService.unreserve("SALES_ORDER", soId, null);

        // ③ 行状态固化 + fulfilled_qty 兜齐 ordered_qty（消除浮点尾差）
        List<Long> lineIds = new ArrayList<>();
        for (SoSalesOrderLine line : lines) {
            lineIds.add(line.getLineId());
            soSalesOrderLineMapper.update(null,
                Wrappers.<SoSalesOrderLine>lambdaUpdate()
                    .eq(SoSalesOrderLine::getLineId, line.getLineId())
                    .set(SoSalesOrderLine::getLineStatus, "FULFILLED")
                    .set(SoSalesOrderLine::getFulfilledQty, line.getOrderedQty()));
        }

        // ④ 生产计划收尾：自制行关联的 PrdProductionPlan → CLOSED
        closeLinkedProductionPlans(lineIds);

        // ⑤ 信用转结 FROZEN → USED（已交付形成应收，仍占用额度）
        creditCheckService.convertToUsed(soId);

        // ⑥ 头状态: 条件更新抢占 CONFIRMED → FULFILLED, 防并发双履行(重复事件/重复核销)
        header.setOrderStatus(STATUS_FULFILLED);
        header.setFulfilledTime(new Date());
        int updated = baseMapper.update(null,
            Wrappers.<SoSalesOrderHeader>lambdaUpdate()
                .set(SoSalesOrderHeader::getOrderStatus, STATUS_FULFILLED)
                .set(SoSalesOrderHeader::getFulfilledTime, header.getFulfilledTime())
                .eq(SoSalesOrderHeader::getSoId, soId)
                .eq(SoSalesOrderHeader::getOrderStatus, STATUS_CONFIRMED));
        if (updated == 0) {
            throw new RuntimeException("订单状态已被并发操作变更，履行失败，请刷新后重试");
        }

        // ⑦ 发布事件（为未来开票/通知预留）
        applicationEventPublisher.publishEvent(new SalesOrderFulfilledEvent(this, soId, header.getSoNo(), TenantHelper.getTenantId()));
        log.info("销售订单履行完成, soId={}, soNo={}, force={}", soId, header.getSoNo(), force);
    }

    /**
     * 关闭销售订单行关联的生产计划（自制物料行）。
     * 仅将非 CLOSED 终态的计划置为 CLOSED；计划不存在或已终态则跳过。
     */
    private void closeLinkedProductionPlans(List<Long> orderLineIds) {
        if (orderLineIds == null || orderLineIds.isEmpty()) {
            return;
        }
        List<PrdProductionPlan> plans = prdProductionPlanMapper.selectList(
            new LambdaQueryWrapper<PrdProductionPlan>()
                .in(PrdProductionPlan::getOrderLineId, orderLineIds));
        for (PrdProductionPlan plan : plans) {
            if (plan.getStatus() == null || !"CLOSED".equals(plan.getStatus())) {
                plan.setStatus("CLOSED");
                prdProductionPlanMapper.updateById(plan);
                log.info("销售订单履行后关闭生产计划, planNo={}, orderLineId={}",
                    plan.getPlanNo(), plan.getOrderLineId());
            }
        }
    }

    /**
     * 关闭销售订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long soId) {
        SoSalesOrderHeader header = baseMapper.selectById(soId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        if (STATUS_CANCELLED.equals(header.getOrderStatus())) {
            throw new RuntimeException("已取消的订单不能关闭");
        }
        // 条件更新: 仅非取消状态可置 CLOSED, 防并发取消/关闭竞态
        header.setOrderStatus(STATUS_CLOSED);
        header.setClosedTime(new Date());
        int updated = baseMapper.update(null,
            Wrappers.<SoSalesOrderHeader>lambdaUpdate()
                .set(SoSalesOrderHeader::getOrderStatus, STATUS_CLOSED)
                .set(SoSalesOrderHeader::getClosedTime, header.getClosedTime())
                .eq(SoSalesOrderHeader::getSoId, soId)
                .ne(SoSalesOrderHeader::getOrderStatus, STATUS_CANCELLED));
        if (updated == 0) {
            throw new RuntimeException("订单已被并发取消，无法关闭");
        }
        creditCheckService.release(soId);
        // 释放库存预留, 避免预留库存泄漏(与 cancel 保持一致)
        stockReservationService.unreserve("SALES_ORDER", soId, null);
    }

    /**
     * 取消销售订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long soId, String reason) {
        SoSalesOrderHeader header = baseMapper.selectById(soId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        if (STATUS_CLOSED.equals(header.getOrderStatus())) {
            throw new RuntimeException("已关闭的订单不能取消");
        }
        if (STATUS_FULFILLED.equals(header.getOrderStatus())) {
            throw new RuntimeException("已履行的订单不能取消，请改用关闭");
        }
        header.setOrderStatus(STATUS_CANCELLED);
        header.setCancelledTime(new Date());
        header.setCancelledReason(reason);
        baseMapper.updateById(header);
        creditCheckService.release(soId);
        // 释放库存预留, 避免预留库存泄漏
        stockReservationService.unreserve("SALES_ORDER", soId, null);

        // 行状态 -> CANCELLED（头尾一致），并关闭关联生产计划（与 fulfill 对称，防取消后计划仍待执行）
        List<SoSalesOrderLine> lines = soSalesOrderLineMapper.selectList(
            new LambdaQueryWrapper<SoSalesOrderLine>().eq(SoSalesOrderLine::getSoId, soId));
        if (lines != null && !lines.isEmpty()) {
            soSalesOrderLineMapper.update(null,
                Wrappers.<SoSalesOrderLine>lambdaUpdate()
                    .eq(SoSalesOrderLine::getSoId, soId)
                    .set(SoSalesOrderLine::getLineStatus, "CANCELLED"));
            List<Long> lineIds = lines.stream().map(SoSalesOrderLine::getLineId).collect(Collectors.toList());
            closeLinkedProductionPlans(lineIds);
        }
    }

    // ==================== Flowable 工作流集成 ====================

    /**
     * 提交审批流程
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String soIdStr, String selectFlowId) {
        Long soId = Long.parseLong(soIdStr);
        SoSalesOrderHeader order = baseMapper.selectById(soId);
        if (order == null) {
            throw new RuntimeException("销售订单不存在");
        }
        if (!STATUS_DRAFT.equals(order.getOrderStatus())) {
            throw new RuntimeException("只有草稿状态的销售订单才能提交审批");
        }
        if (StringUtils.isBlank(selectFlowId)) {
            if (order.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(order.getSelectFlowId());
            } else {
                throw new RuntimeException("请先在编辑页选择审批流程并保存");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", soIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("soId", order.getSoId());
        variables.put("soNo", order.getSoNo());
        variables.put("clientName", order.getClientName());
        variables.put("orderDate", order.getOrderDate());
        R<Void> result = processService.startProcessByDataId(soIdStr, selectFlowId, "soSalesOrderHeaderService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new RuntimeException(result.getMsg());
    }

    /**
     * 获取流程实例ID
     */
    @Override
    public String getProcInsId(String soIdStr) {
        return procInsIdHelper.resolve(soIdStr, () -> {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(soIdStr);
            return business != null ? business.getProcessInstanceId() : null;
        });
    }

    /**
     * 流程审批后处理（更新销售订单状态）
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void afterFlowHandle(WfMyBusiness business) {
        Long soId = Long.parseLong(business.getDataId());
        SoSalesOrderHeader order = baseMapper.selectById(soId);
        if (order == null) {
            return;
        }
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            order.setOrderStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            order.setOrderStatus(STATUS_APPROVED);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            order.setOrderStatus(STATUS_REJECTED);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            order.setOrderStatus(STATUS_DRAFT);
        }
        baseMapper.updateById(order);
    }

    /**
     * 根据数据ID获取业务数据
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
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

    /**
     * Batch insert or update sales order lines
     */
    private void insertOrUpdateLines(Long soId, List<SoSalesOrderLineBo> lineBoList) {
        if (lineBoList != null && !lineBoList.isEmpty()) {
            List<SoSalesOrderLine> lineList = MapstructUtils.convert(lineBoList, SoSalesOrderLine.class);
            for (SoSalesOrderLine line : lineList) {
                line.setLineId(null);
                line.setSoId(soId);
            }
            soSalesOrderLineMapper.insertBatch(lineList);
        }
    }

    /**
     * 销售订单确认后，对库存物料(非自制)触发库存预留
     */
    private void reserveStockForSalesOrder(Long soId) {
        List<SoSalesOrderLine> lines = soSalesOrderLineMapper.selectList(
            new LambdaQueryWrapper<SoSalesOrderLine>()
                .eq(SoSalesOrderLine::getSoId, soId));
        for (SoSalesOrderLine line : lines) {
            if (line.getMaterialId() == null || line.getOrderedQty() == null) {
                continue;
            }
            BdMaterial material = bdMaterialMapper.selectById(line.getMaterialId());
            if (material != null && "PROCESS".equals(material.getSourceType())) {
                continue; // 自制物料走生产计划，不库存预留
            }
            BigDecimal reserved = stockReservationService.reserve(
                line.getMaterialId(), line.getWarehouseId(), line.getOrderedQty(),
                "SALES_ORDER", soId, line.getLineId());
            if (reserved.compareTo(BigDecimal.ZERO) > 0) {
                LambdaUpdateWrapper<SoSalesOrderLine> wrapper = Wrappers.<SoSalesOrderLine>lambdaUpdate()
                    .eq(SoSalesOrderLine::getLineId, line.getLineId())
                    .set(SoSalesOrderLine::getReservedQty, reserved);
                soSalesOrderLineMapper.update(null, wrapper);
            }
            if (reserved.compareTo(line.getOrderedQty()) < 0) {
                log.warn("销售订单行 {} 库存预留不足: 需求={}, 预留={}", line.getLineId(), line.getOrderedQty(), reserved);
            }
        }
    }

    /**
     * 销售订单确认后，对自制物料自动创建生产计划
     */
    private void createProductionPlansFromSalesOrder(Long soId, String soNo) {
        List<SoSalesOrderLine> lines = soSalesOrderLineMapper.selectList(
            new LambdaQueryWrapper<SoSalesOrderLine>()
                .eq(SoSalesOrderLine::getSoId, soId));
        for (SoSalesOrderLine line : lines) {
            if (line.getMaterialId() == null) {
                continue;
            }
            BdMaterial material = bdMaterialMapper.selectById(line.getMaterialId());
            if (material != null && "PROCESS".equals(material.getSourceType())) {
                createProductionPlan(line, soNo, material);
            }
        }
    }

    /**
     * 为加工型物料的销售订单行自动创建生产计划。
     * <p>根据物料查找当前有效BOM并关联，生成草稿状态的MPS计划，供后续生产排程使用。</p>
     *
     * @param line     销售订单行，提供行号与订购数量
     * @param soNo     销售订单单号，作为计划来源与名称前缀
     * @param material 物料信息，用于判断是否加工件及生成计划名称
     */
    private void createProductionPlan(SoSalesOrderLine line, String soNo, BdMaterial material) {
        PrdProductionPlan plan = new PrdProductionPlan();
        plan.setPlanNo(autoCodeUtil.genSerialCode("PRD_PLAN_CODE", null));
        plan.setPlanName("销售订单-" + soNo + "-" + material.getMaterialName());
        plan.setPlanType("MPS");
        plan.setPlanDate(new Date());
        plan.setStatus(STATUS_DRAFT);
        plan.setReferenceNo(soNo);
        plan.setOrderLineId(line.getLineId());
        plan.setOrderLineNo(line.getLineNo());
        plan.setTotalQuantity(line.getOrderedQty());

        // 查找物料当前有效BOM
        LambdaQueryWrapper<BdBom> bomQuery = Wrappers.lambdaQuery();
        bomQuery.eq(BdBom::getParentMaterialId, material.getMaterialId());
        bomQuery.eq(BdBom::getIsCurrent, 1L);
        bomQuery.last("LIMIT 1");
        BdBom bom = bdBomMapper.selectOne(bomQuery);
        if (bom != null) {
            plan.setBomId(bom.getBomId());
            plan.setBomName(bom.getBomName());
            plan.setBomVersion(bom.getBomVersion());
        } else {
            log.warn("物料未配置BOM，生产计划将不关联BOM, materialId={}, materialCode={}",
                material.getMaterialId(), material.getMaterialCode());
        }

        prdProductionPlanMapper.insert(plan);
        log.info("销售订单确认后自动创建生产计划, soNo={}, materialCode={}, planNo={}",
            soNo, material.getMaterialCode(), plan.getPlanNo());
    }

    /**
     * 根据查询条件对象构建销售订单主表的查询构造器。
     * <p>仅对非空条件拼接等值或模糊匹配，便于分页与列表查询复用。</p>
     *
     * @param bo 销售订单查询条件，包含单号、客户、业务员、部门、状态等过滤项
     * @return 已拼接条件的 {@link LambdaQueryWrapper} 查询构造器
     */
    private LambdaQueryWrapper<SoSalesOrderHeader> buildQueryWrapper(SoSalesOrderHeaderBo bo) {
        LambdaQueryWrapper<SoSalesOrderHeader> lqw = Wrappers.<SoSalesOrderHeader>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getSoNo()), SoSalesOrderHeader::getSoNo, bo.getSoNo())
            .eq(bo.getCoId() != null, SoSalesOrderHeader::getCoId, bo.getCoId())
            .eq(bo.getClientId() != null, SoSalesOrderHeader::getClientId, bo.getClientId())
            .like(StringUtils.isNotBlank(bo.getClientName()), SoSalesOrderHeader::getClientName, bo.getClientName())
            .eq(bo.getSalesRepId() != null, SoSalesOrderHeader::getSalesRepId, bo.getSalesRepId())
            .like(StringUtils.isNotBlank(bo.getSalesRepName()), SoSalesOrderHeader::getSalesRepName, bo.getSalesRepName())
            .eq(bo.getDeptId() != null, SoSalesOrderHeader::getDeptId, bo.getDeptId())
            .eq(bo.getOrderDate() != null, SoSalesOrderHeader::getOrderDate, bo.getOrderDate())
            .eq(bo.getPromiseDate() != null, SoSalesOrderHeader::getPromiseDate, bo.getPromiseDate())
            .eq(bo.getRequestedDate() != null, SoSalesOrderHeader::getRequestedDate, bo.getRequestedDate())
            .eq(StringUtils.isNotBlank(bo.getCreditStatus()), SoSalesOrderHeader::getCreditStatus, bo.getCreditStatus())
            .eq(bo.getCreditBlockedBy() != null, SoSalesOrderHeader::getCreditBlockedBy, bo.getCreditBlockedBy())
            .eq(StringUtils.isNotBlank(bo.getOrderStatus()), SoSalesOrderHeader::getOrderStatus, bo.getOrderStatus())
            .eq(bo.getProjectId() != null, SoSalesOrderHeader::getProjectId, bo.getProjectId());
        return lqw;
    }
}
