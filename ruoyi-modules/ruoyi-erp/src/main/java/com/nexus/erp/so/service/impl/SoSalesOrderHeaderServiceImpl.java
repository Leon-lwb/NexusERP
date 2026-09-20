package com.nexus.erp.so.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdBom;
import com.nexus.erp.bd.domain.BdMaterialVendor;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdBomMapper;
import com.nexus.erp.bd.mapper.BdMaterialVendorMapper;
import com.nexus.erp.prd.domain.PrdProductionPlan;
import com.nexus.erp.prd.domain.PrdSubcontract;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdProductionPlanMapper;
import com.nexus.erp.prd.mapper.PrdSubcontractMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.domain.SoCustomerOrder;
import com.nexus.erp.so.domain.bo.SoSalesOrderHeaderBo;
import com.nexus.erp.so.domain.bo.SoSalesOrderLineBo;
import com.nexus.erp.so.domain.vo.SoSalesOrderHeaderVo;
import com.nexus.erp.so.domain.vo.SoSalesOrderLineVo;
import com.nexus.erp.so.event.SalesOrderFulfilledEvent;
import com.nexus.erp.so.mapper.SoCustomerOrderMapper;
import com.nexus.erp.so.mapper.SoSalesOrderHeaderMapper;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ICreditCheckService;
import com.nexus.erp.so.service.ISoSalesOrderHeaderService;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmTransfer;
import com.nexus.erp.wm.domain.WmTransferLine;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmTransferLineMapper;
import com.nexus.erp.wm.mapper.WmTransferMapper;
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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    /** 百分数换算基数（税率/折扣率均以「百分数」存储：13 表示 13%） */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 信用状态：检查通过（与字典 so_credit_status 的 CHECKED_OK 对齐） */
    private static final String CREDIT_STATUS_CHECKED_OK = "CHECKED_OK";

    /** 信用状态：待检查（与字典 so_credit_status 的 NOT_CHECKED 对齐，新增单据的初始值） */
    private static final String CREDIT_STATUS_NOT_CHECKED = "NOT_CHECKED";

    private final SoSalesOrderHeaderMapper baseMapper;
    private final SoSalesOrderLineMapper soSalesOrderLineMapper;
    private final SoCustomerOrderMapper soCustomerOrderMapper;
    private final BdMaterialMapper bdMaterialMapper;
    private final BdBomMapper bdBomMapper;
    private final BdMaterialVendorMapper bdMaterialVendorMapper;
    private final PrdProductionPlanMapper prdProductionPlanMapper;
    private final PrdSubcontractMapper prdSubcontractMapper;
    private final PrdWorkOrderMapper prdWorkOrderMapper;
    private final WmWarehouseMapper wmWarehouseMapper;
    private final WmMaterialStockMapper wmMaterialStockMapper;
    private final WmTransferMapper wmTransferMapper;
    private final WmTransferLineMapper wmTransferLineMapper;
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
            // 回填客户订单号（展示用，避免前端用客户参考号冒充客户订单号）
            if (vo.getCoId() != null) {
                SoCustomerOrder customerOrder = soCustomerOrderMapper.selectById(vo.getCoId());
                if (customerOrder != null) {
                    vo.setCoNo(customerOrder.getCoNo());
                }
            }
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
        // 状态强制 DRAFT：订单状态只能经 confirm / 审批流 / close / cancel 流转。
        // 原实现允许客户端直写终态（sys_oper_log 中已有 orderStatus=CONFIRMED 直接建单的记录），
        // 那样会绕过信用额度检查、库存预留与生产计划生成。
        entity.setOrderStatus(STATUS_DRAFT);
        // 生命周期时间戳由对应动作写入（confirm/fulfill/close/cancel），
        // 信用检查结果由信用检查服务在 confirm 时写入 —— 新增时一律复位，
        // 不接受客户端伪造（否则草稿单上会出现假的「确认时间/履行时间」或「已检查」）
        entity.setConfirmedTime(null);
        entity.setFulfilledTime(null);
        entity.setClosedTime(null);
        entity.setCancelledTime(null);
        entity.setCreditStatus(CREDIT_STATUS_NOT_CHECKED);
        entity.setCreditCheckTime(null);
        entity.setCreditBlockedBy(null);
        if (StringUtils.isBlank(entity.getSoNo())) {
            entity.setSoNo(autoCodeUtil.genSerialCode("SO_ORDER_CODE", null));
        }
        // 金额一律服务端重算，不信任客户端传入的 totalAmount/taxAmount/discountAmount。
        // 新增时明细为 null 视作「空明细」，金额归零（不能因为没传明细就把客户端的伪造金额留下）
        recalcAmounts(entity, bo.getSoSalesOrderLineList() == null ? List.of() : bo.getSoSalesOrderLineList());
        int rows = baseMapper.insert(entity);
        insertLines(entity.getSoId(), bo.getSoSalesOrderLineList());
        return rows;
    }

    /**
     * 修改销售订单。
     * <p>两条硬约束：</p>
     * <ol>
     *   <li><b>仅草稿可改</b>（决策 D4）：单号与状态由服务端权威维护，不接受客户端改写；</li>
     *   <li><b>行明细按 lineId 差量更新</b>（决策 D3）：不再「逻辑删除全部行 + 重新插入新行」，
     *       因为下游全部按 {@code line_id} 关联 —— 生产计划 {@code order_line_id}、出库回写
     *       {@code addShippedQty(soLineId)}、发票行 {@code so_line_id}。整体重建会让这些关联
     *       指向已删除的行，导致生产计划失联、发货数量回写到已删行使订单永远无法履行。</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoSalesOrderHeaderBo bo) {
        if (bo.getSoId() == null) {
            throw new ServiceException("销售订单ID不能为空");
        }
        SoSalesOrderHeader existing = baseMapper.selectById(bo.getSoId());
        if (existing == null) {
            throw new ServiceException("销售订单不存在");
        }
        if (!STATUS_DRAFT.equals(existing.getOrderStatus())) {
            throw new ServiceException("只有草稿状态的销售订单才能修改，当前状态：" + existing.getOrderStatus());
        }
        SoSalesOrderHeader entity = MapstructUtils.convert(bo, SoSalesOrderHeader.class);
        // 单号与状态服务端权威：客户端传了也还原，防止改单顺带改单号/绕过状态机
        entity.setSoNo(existing.getSoNo());
        entity.setOrderStatus(existing.getOrderStatus());
        // 生命周期时间戳与信用检查结果同样服务端权威：改单不得改写（与单号/状态同理）
        entity.setConfirmedTime(existing.getConfirmedTime());
        entity.setFulfilledTime(existing.getFulfilledTime());
        entity.setClosedTime(existing.getClosedTime());
        entity.setCancelledTime(existing.getCancelledTime());
        entity.setCreditStatus(existing.getCreditStatus());
        entity.setCreditCheckTime(existing.getCreditCheckTime());
        entity.setCreditBlockedBy(existing.getCreditBlockedBy());
        // 明细为 null 表示「本次不提交明细」——保持原样、不动表头金额；
        // 只有显式传空数组才是「清空全部明细」。口径与合同模块（SoContractServiceImpl.updateByBo
        // 的 `if (bo.getSoContractLineList() != null)`）一致。
        // 修复前：null 会让 diffUpdateLines 把所有已有行都当「请求中未出现」而逻辑删除，
        // 且 recalcAmounts 把表头金额算成 0 —— API 客户端做部分更新（例如只改备注）会静默清空整单明细与金额。
        if (bo.getSoSalesOrderLineList() != null) {
            recalcAmounts(entity, bo.getSoSalesOrderLineList());
            diffUpdateLines(entity.getSoId(), bo.getSoSalesOrderLineList());
        } else {
            // 明细未提交 -> 金额保持库中原值，同样不接受客户端改写
            // （否则跳过重算会让客户端的 totalAmount 直接落库，等于重新打开 T1.8 堵掉的伪造口子）
            entity.setTotalAmount(existing.getTotalAmount());
            entity.setTaxAmount(existing.getTaxAmount());
            entity.setDiscountAmount(existing.getDiscountAmount());
            entity.setDiscountRate(existing.getDiscountRate());
        }
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除销售订单（同时删除行明细）。
     * <p>仅草稿可删除：非草稿订单已产生信用占用、库存预留、生产计划等副作用，
     * 必须走 {@code cancel}/{@code close} 才能完整收尾（释放信用、释放预留、关闭计划），
     * 直接删除会把这些副作用变成孤儿数据。</p>
     * <p>即便如此，仍对草稿单做一次防御性收尾，兜住历史脏数据（例如曾被旧版无门禁的改单/删除逻辑破坏的单据）。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        List<Long> idList = Arrays.stream(ids).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (idList.isEmpty()) {
            return 0;
        }
        List<SoSalesOrderHeader> orders = baseMapper.selectBatchIds(idList);
        for (SoSalesOrderHeader order : orders) {
            if (!STATUS_DRAFT.equals(order.getOrderStatus())) {
                throw new ServiceException("只有草稿状态的销售订单才能删除，单号 "
                    + order.getSoNo() + " 当前状态：" + order.getOrderStatus());
            }
        }
        // 防御性收尾：释放信用额度与库存预留（草稿正常无这些副作用）
        for (SoSalesOrderHeader order : orders) {
            creditCheckService.release(order.getSoId());
            stockReservationService.unreserve("SALES_ORDER", order.getSoId(), null);
        }
        // 关闭可能残留的关联生产计划，避免计划挂在一张已删除的订单行上
        List<Long> lineIds = soSalesOrderLineMapper.selectList(
                new LambdaQueryWrapper<SoSalesOrderLine>().in(SoSalesOrderLine::getSoId, idList))
            .stream()
            .map(SoSalesOrderLine::getLineId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        if (!lineIds.isEmpty()) {
            closeLinkedProductionPlans(lineIds);
        }
        soSalesOrderLineMapper.delete(new LambdaQueryWrapper<SoSalesOrderLine>()
            .in(SoSalesOrderLine::getSoId, idList));
        return baseMapper.deleteBatchIds(idList);
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
        // 未关联客户则无从做信用检查（checkAndFreeze 对 clientId=null 会静默跳过，
        // 等于绕过信用额度控制），因此直接拒绝并要求补全客户
        if (header.getClientId() == null) {
            throw new ServiceException("订单未关联来源客户，无法执行信用检查，请先补全客户后重试");
        }

        // 信用检查与冻结
        creditCheckService.checkAndFreeze(header.getClientId(), soId,
            header.getTotalAmount() != null ? header.getTotalAmount() : BigDecimal.ZERO);
        // 与字典 so_credit_status 对齐（NOT_CHECKED/CHECKED_OK/CHECKED_BLOCKED）。
        // 原实现写 "PASSED"，字典中不存在该值，前端 dict-tag 只能显示原始码。
        header.setCreditStatus(CREDIT_STATUS_CHECKED_OK);
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

        // 履约链路分流（按物料来源类型）
        createProductionPlansFromSalesOrder(soId, header.getSoNo());
        createSubcontractsFromSalesOrder(soId, header.getSoNo());
        createTransfersFromSalesOrder(soId, header.getSoNo());
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
     * 流程审批后处理（更新销售订单状态）。
     *
     * <p><b>条件更新</b>：只在订单仍处于「流程在途」状态（DRAFT/APPROVING/APPROVED/REJECTED）时才改写，
     * 不覆盖业务终态与已确认状态。原实现是无条件 {@code updateById}，存在两条可复现的覆盖路径：</p>
     * <ul>
     *   <li>订单在 APPROVING 时被 {@code cancel()} 取消（cancel 允许取消 APPROVING），
     *       随后审批人撤销流程 → 回调把 CANCELLED 改回 DRAFT，<b>已取消的订单被"复活"</b>；</li>
     *   <li>订单 APPROVED 后被 {@code confirm()} 确认（已做信用冻结、库存预留、生成生产计划），
     *       随后审批人撤销流程 → 回调把 CONFIRMED 改回 DRAFT，用户可再次确认
     *       → <b>重复冻结额度、重复预留库存、重复生成生产计划</b>。</li>
     * </ul>
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
        String newStatus;
        if (ActStatus.doing.equals(actStatus)) {
            newStatus = STATUS_APPROVING;
        } else if (ActStatus.pass.equals(actStatus)) {
            newStatus = STATUS_APPROVED;
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            newStatus = STATUS_REJECTED;
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            newStatus = STATUS_DRAFT;
        } else {
            return;
        }
        int updated = baseMapper.update(null,
            Wrappers.<SoSalesOrderHeader>lambdaUpdate()
                .set(SoSalesOrderHeader::getOrderStatus, newStatus)
                .eq(SoSalesOrderHeader::getSoId, soId)
                .in(SoSalesOrderHeader::getOrderStatus,
                    STATUS_DRAFT, STATUS_APPROVING, STATUS_APPROVED, STATUS_REJECTED));
        if (updated == 0) {
            log.warn("销售订单[{}]当前状态[{}]不允许流程回调改写为[{}]，忽略本次变更",
                soId, order.getOrderStatus(), newStatus);
        }
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
     * 服务端重算订单金额，不信任客户端传入的 {@code totalAmount/taxAmount/discountAmount}。
     * <p>口径与前端 {@code orderheader/form.vue} 的 calcLine/calcHeader 完全一致（折扣税前扣，Model A）：</p>
     * <pre>
     *   行金额     = 数量 × 单价
     *   行折扣额   = 行金额 × 行折扣率% / 100
     *   行税额     = (行金额 − 行折扣额) × 税率% / 100
     *   净额       = Σ(行金额 − 行折扣额)
     *   税款       = Σ 行税额
     *   价税合计   = 净额 + 税款
     *   整单折扣额 = 价税合计 × 整单折扣率% / 100
     *   含税总金额 = 价税合计 − 整单折扣额
     * </pre>
     * <p><b>为什么必须服务端算</b>：{@code confirm()} 用 {@code totalAmount} 做信用额度校验，
     * 客户端传 0 或负数即可绕过信用控制；行金额同理会被下游开票/报表直接引用。</p>
     * <p>副作用：会把重算结果写回传入的 lineBo（供后续白名单落库使用）。</p>
     * <p><b>约定</b>：{@code lineBoList == null} 表示「本次未提交明细」，此时<b>不动</b>表头金额
     * （部分更新场景）；要表达「无明细」请传空集合，金额会归零。</p>
     *
     * @param header     订单头（重算结果写回 totalAmount/taxAmount/discountAmount）
     * @param lineBoList 订单行（重算结果写回 lineAmount/discountAmount/taxAmount）；null 表示不提交明细
     */
    private void recalcAmounts(SoSalesOrderHeader header, List<SoSalesOrderLineBo> lineBoList) {
        if (lineBoList == null) {
            return;
        }
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (SoSalesOrderLineBo line : lineBoList) {
            BigDecimal lineAmount = round2(nullToZero(line.getOrderedQty()).multiply(nullToZero(line.getUnitPrice())));
            BigDecimal lineDiscount = round2(lineAmount.multiply(nullToZero(line.getDiscountPercent()))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP));
            BigDecimal lineTax = round2(lineAmount.subtract(lineDiscount)
                .multiply(nullToZero(line.getTaxRate())).divide(HUNDRED, 2, RoundingMode.HALF_UP));
            line.setLineAmount(lineAmount);
            line.setDiscountAmount(lineDiscount);
            line.setTaxAmount(lineTax);
            net = net.add(lineAmount).subtract(lineDiscount);
            tax = tax.add(lineTax);
        }
        BigDecimal gross = round2(net.add(tax));
        BigDecimal headerDiscount = round2(gross.multiply(nullToZero(header.getDiscountRate()))
            .divide(HUNDRED, 2, RoundingMode.HALF_UP));
        header.setTaxAmount(round2(tax));
        header.setDiscountAmount(headerDiscount);
        header.setTotalAmount(round2(gross.subtract(headerDiscount)));
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal round2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 批量新增销售订单行（仅新增场景）：清空行ID、归属到订单，并初始化履约/关联字段。
     */
    private void insertLines(Long soId, List<SoSalesOrderLineBo> lineBoList) {
        if (lineBoList == null || lineBoList.isEmpty()) {
            return;
        }
        List<SoSalesOrderLine> lineList = new ArrayList<>(lineBoList.size());
        for (SoSalesOrderLineBo lineBo : lineBoList) {
            SoSalesOrderLine line = MapstructUtils.convert(lineBo, SoSalesOrderLine.class);
            line.setLineId(null);
            line.setSoId(soId);
            resetServerOwnedLineFields(line);
            lineList.add(line);
        }
        soSalesOrderLineMapper.insertBatch(lineList);
    }

    /**
     * 行明细差量更新：按 {@code lineId} 区分「更新 / 新增 / 删除」，保留已有行的 {@code line_id}。
     * <p>安全要点：</p>
     * <ul>
     *   <li>只在本单已有行里按 lineId 匹配，外部/他单的 lineId 一律按新增处理（lineId 重置为 null），
     *       避免越权改写他单明细；</li>
     *   <li>删除前先对<b>全部</b>待删行做守卫校验再执行，避免删一半失败留下半成品；</li>
     *   <li>新增行与删除守卫都不接受客户端传入的履约/关联字段。</li>
     * </ul>
     */
    private void diffUpdateLines(Long soId, List<SoSalesOrderLineBo> lineBoList) {
        List<SoSalesOrderLine> existingLines = soSalesOrderLineMapper.selectList(
            new LambdaQueryWrapper<SoSalesOrderLine>().eq(SoSalesOrderLine::getSoId, soId));
        Map<Long, SoSalesOrderLine> existingById = new HashMap<>();
        for (SoSalesOrderLine line : existingLines) {
            if (line.getLineId() != null) {
                existingById.put(line.getLineId(), line);
            }
        }

        List<SoSalesOrderLine> toInsert = new ArrayList<>();
        Set<Long> keptLineIds = new HashSet<>();
        if (lineBoList != null) {
            for (SoSalesOrderLineBo lineBo : lineBoList) {
                SoSalesOrderLine existing = lineBo.getLineId() == null ? null : existingById.get(lineBo.getLineId());
                if (existing == null) {
                    SoSalesOrderLine created = MapstructUtils.convert(lineBo, SoSalesOrderLine.class);
                    created.setLineId(null);
                    created.setSoId(soId);
                    resetServerOwnedLineFields(created);
                    toInsert.add(created);
                    continue;
                }
                applyEditableLineFields(existing, lineBo);
                soSalesOrderLineMapper.updateById(existing);
                keptLineIds.add(existing.getLineId());
            }
        }

        List<SoSalesOrderLine> toDelete = new ArrayList<>();
        for (SoSalesOrderLine line : existingLines) {
            if (line.getLineId() != null && !keptLineIds.contains(line.getLineId())) {
                assertLineDeletable(line);
                toDelete.add(line);
            }
        }
        for (SoSalesOrderLine line : toDelete) {
            soSalesOrderLineMapper.deleteById(line.getLineId());
        }
        if (!toInsert.isEmpty()) {
            soSalesOrderLineMapper.insertBatch(toInsert);
        }
    }

    /**
     * 已有行仅允许更新白名单字段；履约进度与关联字段保持数据库原值（防表单快照覆盖并发已发货量）。
     * <p>已进入履约的行（有发货/投产/开票）额外禁止修改物料与订单数量 —— 改小会与已发数量矛盾。</p>
     */
    private void applyEditableLineFields(SoSalesOrderLine target, SoSalesOrderLineBo source) {
        if (isFulfillmentLocked(target)) {
            if (!Objects.equals(target.getMaterialId(), source.getMaterialId())
                || !sameDecimal(target.getOrderedQty(), source.getOrderedQty())) {
                throw new ServiceException("第 " + target.getLineNo() + " 行已发生发货/投产/开票，不允许修改物料或订单数量");
            }
        } else {
            target.setMaterialId(source.getMaterialId());
            target.setMaterialCode(source.getMaterialCode());
            target.setMaterialName(source.getMaterialName());
            target.setMaterialSpec(source.getMaterialSpec());
            target.setOrderedQty(source.getOrderedQty());
            target.setUnitPrice(source.getUnitPrice());
            target.setUnit(source.getUnit());
        }
        target.setLineNo(source.getLineNo());
        target.setLineAmount(source.getLineAmount());
        target.setTaxRate(source.getTaxRate());
        target.setTaxAmount(source.getTaxAmount());
        target.setDiscountPercent(source.getDiscountPercent());
        target.setDiscountAmount(source.getDiscountAmount());
        target.setRequestedDate(source.getRequestedDate());
        target.setPromiseDate(source.getPromiseDate());
        target.setWarehouseId(source.getWarehouseId());
        target.setDeliveryPriority(source.getDeliveryPriority());
        target.setCustomerLineRef(source.getCustomerLineRef());
        target.setCustomerProductCode(source.getCustomerProductCode());
        target.setCustomerProductName(source.getCustomerProductName());
        target.setSortOrder(source.getSortOrder());
        target.setRemark(source.getRemark());
        target.setAttr1(source.getAttr1());
        target.setAttr2(source.getAttr2());
        target.setAttr3(source.getAttr3());
        target.setAttr4(source.getAttr4());
    }

    /**
     * 履约/关联类字段：服务端权威，任何情况下都不接受客户端传入（新增行强制初始化）。
     */
    private void resetServerOwnedLineFields(SoSalesOrderLine line) {
        line.setDeliveredQty(BigDecimal.ZERO);
        line.setInvoicedQty(BigDecimal.ZERO);
        line.setReturnedQty(BigDecimal.ZERO);
        line.setFulfilledQty(BigDecimal.ZERO);
        line.setProductionQty(BigDecimal.ZERO);
        line.setPurchaseQty(BigDecimal.ZERO);
        line.setReservedQty(BigDecimal.ZERO);
        line.setLineStatus("PENDING");
        line.setLinkedMoId(null);
        line.setReservedBatchNo(null);
        line.setOqcStatus(null);
        line.setOqcId(null);
        line.setOqcCode(null);
        line.setOqcTime(null);
    }

    /**
     * 删除守卫：已进入履约的行、已关联生产计划的行禁止删除。
     */
    private void assertLineDeletable(SoSalesOrderLine line) {
        if (isFulfillmentLocked(line)) {
            throw new ServiceException("第 " + line.getLineNo() + " 行已发生发货/投产/开票，不允许删除");
        }
        Long planCount = prdProductionPlanMapper.selectCount(
            new LambdaQueryWrapper<PrdProductionPlan>()
                .eq(PrdProductionPlan::getOrderLineId, line.getLineId()));
        if (planCount != null && planCount > 0) {
            throw new ServiceException("第 " + line.getLineNo() + " 行已关联生产计划，不允许删除");
        }
    }

    /** 行是否已进入履约（已发货 / 已投产 / 已开票任一为正） */
    private boolean isFulfillmentLocked(SoSalesOrderLine line) {
        return isPositive(line.getDeliveredQty())
            || isPositive(line.getProductionQty())
            || isPositive(line.getInvoicedQty());
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean sameDecimal(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    /**
     * 销售订单确认后，对库存物料触发库存预留。
     *
     * <p><b>按物料来源类型分流</b>（{@code bd_material.source_type}，字典 {@code prd_material_source_type}
     * 共 6 种）：</p>
     * <ul>
     *   <li>{@code PROCESS} 加工 —— 走生产计划（{@link #createProductionPlansFromSalesOrder}），不预留库存；</li>
     *   <li>{@code SUBCONTRACT} 委外 —— 走委外单（{@link #createSubcontractsFromSalesOrder}），不预留成品库存；</li>
     *   <li>{@code TRANSFER} 调拨 —— 走调拨单（{@link #createTransfersFromSalesOrder}），不预留库存；</li>
     *   <li>{@code PROCUREMENT} 采购 / {@code CFM} 客户供料 / {@code PHANTOM} 虚拟件 —— 预留库存。</li>
     * </ul>
     *
     * <p>原实现只排除 {@code PROCESS}，于是委外/调拨行也会尝试预留库存：
     * 这两类行的货源本就不是本仓现货，预留要么失败（只有 warn）要么错误占用现货，
     * 与它们各自的自动建单链路叠加后还会重复占用。</p>
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
            String sourceType = material == null ? null : material.getSourceType();
            if (isProductionSourced(sourceType) || isSubcontractSourced(sourceType) || isTransferSourced(sourceType)) {
                // 自制走生产计划、委外走委外单、调拨走调拨单，均不占用本仓现货
                continue;
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

    /** 物料来源类型是否为「加工」（走生产计划） */
    private boolean isProductionSourced(String sourceType) {
        return "PROCESS".equals(sourceType);
    }

    /** 物料来源类型是否为「委外」（走委外单） */
    private boolean isSubcontractSourced(String sourceType) {
        return "SUBCONTRACT".equals(sourceType);
    }

    /** 物料来源类型是否为「调拨」（走调拨单） */
    private boolean isTransferSourced(String sourceType) {
        return "TRANSFER".equals(sourceType);
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
            if (material != null && isProductionSourced(material.getSourceType())) {
                createProductionPlan(line, soNo, material);
            }
        }
    }

    /**
     * 销售订单确认后，对委外物料自动创建委外订单（草稿）。
     *
     * <p><b>供应商取值</b>（决策 Q8-A）：优先 {@code bd_material_vendor} 中
     * {@code status=1 且 is_default=1} 的记录；无默认则按 {@code priority} 升序取第一条；
     * 都没有则供应商留空并 warn —— 单据仍生成，便于后续人工补全（委外单的
     * {@code release()} 不校验供应商，不会因此卡住）。</p>
     *
     * <p>数量取订单行订购量，单价取物料-供应商关系的协议价（无则 0），
     * 收货目标仓取订单行上的默认仓库。</p>
     */
    private void createSubcontractsFromSalesOrder(Long soId, String soNo) {
        List<SoSalesOrderLine> lines = soSalesOrderLineMapper.selectList(
            new LambdaQueryWrapper<SoSalesOrderLine>()
                .eq(SoSalesOrderLine::getSoId, soId));
        for (SoSalesOrderLine line : lines) {
            if (line.getMaterialId() == null) {
                continue;
            }
            BdMaterial material = bdMaterialMapper.selectById(line.getMaterialId());
            if (material == null || !isSubcontractSourced(material.getSourceType())) {
                continue;
            }
            createSubcontract(line, soNo, material);
        }
    }

    /**
     * 为委外型物料的销售订单行自动创建委外订单（草稿态）。
     *
     * @param line     销售订单行，提供行号、订购数量与收货目标仓
     * @param soNo     销售订单单号，作为委外单备注来源
     * @param material 物料信息，用于取默认供应商与协议价
     */
    private void createSubcontract(SoSalesOrderLine line, String soNo, BdMaterial material) {
        BdMaterialVendor vendor = resolveDefaultVendor(material.getMaterialId());
        if (vendor == null) {
            log.warn("物料未配置可用供应商，委外单将不关联供应商（可在委外单中补全）, materialId={}, materialCode={}",
                material.getMaterialId(), material.getMaterialCode());
        }

        PrdSubcontract subcontract = new PrdSubcontract();
        subcontract.setSubcontractNo(autoCodeUtil.genSerialCode("PRD_SUBCONTRACT_CODE", null));
        if (vendor != null) {
            subcontract.setVendorId(vendor.getVendorId());
            subcontract.setVendorCode(vendor.getVendorCode());
            subcontract.setVendorName(vendor.getVendorName());
            subcontract.setUnitPrice(vendor.getPrice());
        }
        subcontract.setMaterialId(material.getMaterialId());
        subcontract.setMaterialCode(material.getMaterialCode());
        subcontract.setMaterialName(material.getMaterialName());
        subcontract.setMaterialSpec(material.getMaterialSpec());
        subcontract.setBaseUomName(line.getUnit());
        subcontract.setPlannedQty(line.getOrderedQty());
        subcontract.setStatus("DRAFT");
        subcontract.setPlanStartDate(line.getRequestedDate());
        subcontract.setPlanEndDate(line.getPromiseDate());
        subcontract.setTargetWarehouseId(line.getWarehouseId());
        if (line.getWarehouseId() != null) {
            WmWarehouse warehouse = wmWarehouseMapper.selectById(line.getWarehouseId());
            if (warehouse != null) {
                subcontract.setTargetWarehouseName(warehouse.getWarehouseName());
            }
        }
        if (subcontract.getPlannedQty() != null && subcontract.getUnitPrice() != null) {
            subcontract.setTotalAmount(subcontract.getPlannedQty().multiply(subcontract.getUnitPrice()));
        }
        subcontract.setRemark("销售订单 " + soNo + " 行 " + line.getLineNo() + " 确认后自动生成");
        prdSubcontractMapper.insert(subcontract);
        log.info("销售订单确认后自动创建委外单, soNo={}, materialCode={}, subcontractNo={}, vendorId={}",
            soNo, material.getMaterialCode(), subcontract.getSubcontractNo(), subcontract.getVendorId());
    }

    /**
     * 解析物料的默认供应商（决策 Q8-A）：{@code status=1 且 is_default=1} 优先，
     * 其次按 {@code priority} 升序取第一条启用记录；都没有返回 null。
     */
    private BdMaterialVendor resolveDefaultVendor(Long materialId) {
        List<BdMaterialVendor> candidates = bdMaterialVendorMapper.selectList(
            Wrappers.<BdMaterialVendor>lambdaQuery()
                .eq(BdMaterialVendor::getMaterialId, materialId)
                .eq(BdMaterialVendor::getStatus, 1L)
                .orderByDesc(BdMaterialVendor::getIsDefault)
                .orderByAsc(BdMaterialVendor::getPriority));
        return candidates == null || candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * 销售订单确认后，对调拨物料自动创建调拨单（草稿）。
     *
     * <p><b>仓库取值</b>（决策 Q8-A）：目的仓取订单行上的默认仓库（{@code warehouseId}，
     * 即销售出货的目标仓）；来源仓取该物料**可用库存最多的仓**（真有货可调）。
     * 两者相同、或找不到任何有货的其他仓时**不建单**，只记 warn 交由人工处理 ——
     * 避免生成「同仓调拨」这类无意义的单据。</p>
     */
    private void createTransfersFromSalesOrder(Long soId, String soNo) {
        List<SoSalesOrderLine> lines = soSalesOrderLineMapper.selectList(
            new LambdaQueryWrapper<SoSalesOrderLine>()
                .eq(SoSalesOrderLine::getSoId, soId));
        for (SoSalesOrderLine line : lines) {
            if (line.getMaterialId() == null) {
                continue;
            }
            BdMaterial material = bdMaterialMapper.selectById(line.getMaterialId());
            if (material == null || !isTransferSourced(material.getSourceType())) {
                continue;
            }
            createTransfer(line, soNo, material);
        }
    }

    /**
     * 为调拨型物料的销售订单行自动创建调拨单（草稿态）。
     *
     * @param line     销售订单行，提供订购数量与目的仓
     * @param soNo     销售订单单号，作为调拨单备注来源
     * @param material 物料信息
     */
    private void createTransfer(SoSalesOrderLine line, String soNo, BdMaterial material) {
        Long toWarehouseId = line.getWarehouseId();
        if (toWarehouseId == null) {
            log.warn("调拨型物料未指定默认仓库，无法自动建调拨单，请人工处理: soNo={}, materialCode={}",
                soNo, material.getMaterialCode());
            return;
        }
        // 来源仓：该物料可用库存（在库 - 已预留）最多、且不是目的仓的仓
        WmMaterialStock source = resolveSourceStock(material.getMaterialId(), toWarehouseId);
        if (source == null) {
            log.warn("调拨型物料在目的仓之外无可用库存，无法自动建调拨单，请人工处理: soNo={}, materialCode={}, toWarehouseId={}",
                soNo, material.getMaterialCode(), toWarehouseId);
            return;
        }

        WmTransfer transfer = new WmTransfer();
        transfer.setTransferNo(autoCodeUtil.genSerialCode("WM_TRANSFER_CODE", null));
        transfer.setFromWarehouseId(source.getWarehouseId());
        transfer.setFromWarehouseCode(source.getWarehouseCode());
        transfer.setFromWarehouseName(source.getWarehouseName());
        transfer.setToWarehouseId(toWarehouseId);
        WmWarehouse toWarehouse = wmWarehouseMapper.selectById(toWarehouseId);
        if (toWarehouse != null) {
            transfer.setToWarehouseCode(toWarehouse.getWarehouseCode());
            transfer.setToWarehouseName(toWarehouse.getWarehouseName());
        }
        transfer.setTransferType("WAREHOUSE");
        transfer.setTotalQuantity(line.getOrderedQty());
        transfer.setStatus("DRAFT");
        transfer.setTransferDate(new Date());
        transfer.setRemark("销售订单 " + soNo + " 行 " + line.getLineNo() + " 确认后自动生成");
        wmTransferMapper.insert(transfer);

        WmTransferLine transferLine = new WmTransferLine();
        transferLine.setTransferId(transfer.getTransferId());
        transferLine.setMaterialId(material.getMaterialId());
        transferLine.setMaterialCode(material.getMaterialCode());
        transferLine.setMaterialName(material.getMaterialName());
        transferLine.setMaterialSpec(material.getMaterialSpec());
        transferLine.setBaseUomName(line.getUnit());
        transferLine.setQuantity(line.getOrderedQty());
        transferLine.setRemark("销售订单 " + soNo + " 行 " + line.getLineNo());
        wmTransferLineMapper.insert(transferLine);
        log.info("销售订单确认后自动创建调拨单, soNo={}, materialCode={}, transferNo={}, from={}, to={}",
            soNo, material.getMaterialCode(), transfer.getTransferNo(),
            source.getWarehouseId(), toWarehouseId);
    }

    /**
     * 解析调拨来源仓：该物料可用库存（在库 − 已预留）为正、且不是目的仓的仓中取可用量最大者。
     *
     * @param materialId    物料ID
     * @param excludeWarehouseId 需排除的仓库（目的仓）
     * @return 可用库存最多的库存行；无可选仓时返回 null
     */
    private WmMaterialStock resolveSourceStock(Long materialId, Long excludeWarehouseId) {
        List<WmMaterialStock> stocks = wmMaterialStockMapper.selectList(
            Wrappers.<WmMaterialStock>lambdaQuery()
                .eq(WmMaterialStock::getMaterialId, materialId));
        WmMaterialStock best = null;
        BigDecimal bestAvailable = BigDecimal.ZERO;
        for (WmMaterialStock stock : stocks) {
            if (stock.getWarehouseId() == null || stock.getWarehouseId().equals(excludeWarehouseId)) {
                continue;
            }
            BigDecimal available = nz(stock.getStockQuantity()).subtract(nz(stock.getReservedQuantity()));
            if (available.compareTo(BigDecimal.ZERO) > 0 && available.compareTo(bestAvailable) > 0) {
                best = stock;
                bestAvailable = available;
            }
        }
        return best;
    }

    /** null-safe BigDecimal 取零值 */
    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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
