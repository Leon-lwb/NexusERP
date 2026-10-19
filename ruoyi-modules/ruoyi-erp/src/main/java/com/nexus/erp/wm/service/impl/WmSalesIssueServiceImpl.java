package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.message.OqcAutoCreateMessage;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmSalesIssue;
import com.nexus.erp.wm.domain.WmSalesIssueLine;
import com.nexus.erp.wm.domain.bo.WmSalesIssueBo;
import com.nexus.erp.wm.domain.bo.WmSalesIssueLineBo;
import com.nexus.erp.wm.domain.vo.WmSalesIssueVo;
import com.nexus.erp.wm.domain.vo.WmSalesIssueLineVo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.mapper.WmSalesIssueLineMapper;
import com.nexus.erp.wm.mapper.WmSalesIssueMapper;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.service.SalesIssueSourceGuard;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.wm.service.IWmSalesIssueService;
import com.nexus.erp.so.service.ISoFulfillmentWritebackService;
import com.nexus.workflow.domain.WfMyBusiness;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 销售出库Service业务层处理
 *
 * @author nexus
 * @date 2026-04-03
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("wmSalesIssueService")
public class WmSalesIssueServiceImpl implements IWmSalesIssueService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.nexus.erp.wm.service.WmSerialInventoryService serialInventory;

    /**
     * 基础数据访问映射器（Mapper）
     */
    private final WmSalesIssueMapper baseMapper;

    /**
     * 行数据访问映射器（Mapper）
     */
    private final WmSalesIssueLineMapper lineMapper;

    /**
     * 物料库存数据访问映射器（Mapper）
     */
    private final WmMaterialStockMapper wmMaterialStockMapper;

    /**
     * 库存流水数据访问映射器（Mapper）
     */
    private final WmMaterialStockTxMapper stockTxMapper;

    /**
     * WM 公共依赖（AutoCodeUtil / 流程服务 / 流程实例查询）
     */
    private final WmServiceHelper wm;

    /**
     * 销售发料业务服务，提供销售发料相关能力
     */
    private final ISoFulfillmentWritebackService fulfillmentWritebackService;

    /**
     * 可靠消息生产者：销售出库审批通过后发 OQC 自动创建消息
     */
    private final ReliableMessageProducer messageProducer;

    /**
     * 物料主数据 Mapper：查 inspection_required 门控 OQC 自动创建
     */
    private final BdMaterialMapper bdMaterialMapper;

    /**
     * SO 行 Mapper（出库完成时同步消耗 SO 行预留数量）
     */
    private final SoSalesOrderLineMapper salesOrderLineMapper;

    /**
     * 库存计价服务（ERP-WM-01: 出库成本结转，与 SalesOutStrategy 完全同口径）
     */
    private final com.nexus.erp.wm.service.IInventoryValuationService valuationService;

    /**
     * 实际出库入口的来源校验与订单悲观锁。
     */
    private final SalesIssueSourceGuard sourceGuard;

    /**
     * 实际库存批次已发数量的原子累计。
     */
    private final WmBatchMapper batchMapper;

    /**
     * 分页查询销售发料列表
     *
     * @param bo 销售发料业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @Override
    public TableDataInfo<WmSalesIssueVo> selectPageList(WmSalesIssueBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmSalesIssue> lqw = buildQueryWrapper(bo);
        Page<WmSalesIssueVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询销售发料列表
     *
     * @param bo 销售发料业务对象（BO）
     * @return 结果对象
     */
    @Override
    public List<WmSalesIssueVo> selectList(WmSalesIssueBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 查询销售发料
     *
     * @param id 主键 ID
     * @return 结果
     */
    @Override
    public WmSalesIssueVo selectById(Long id) {
        WmSalesIssueVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            List<WmSalesIssueLine> lineList =
                    lineMapper.selectList(
                            new LambdaQueryWrapper<WmSalesIssueLine>()
                                    .eq(WmSalesIssueLine::getIssueId, id));
            vo.setWmSalesIssueLineList(MapstructUtils.convert(lineList, WmSalesIssueLineVo.class));
        }
        return vo;
    }

    /**
     * 新增销售发料
     *
     * @param bo 销售发料业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmSalesIssueBo bo) {
        WmSalesIssue entity = MapstructUtils.convert(bo, WmSalesIssue.class);
        if (StringUtils.isBlank(entity.getIssueCode())) {
            entity.setIssueCode(wm.autoCodeUtil.genSerialCode("WM_SALES_ISSUE_CODE", null));
        }
        // ERP-WM-09: status 不由 BO 直传，服务端强制草稿
        // （修复前可直造 COMPLETED：从未扣库存却显示"已完成"，completeSalesIssue 也不会再执行）
        entity.setStatus(STATUS_DRAFT);
        int rows = baseMapper.insert(entity);
        insertLines(entity.getIssueId(), bo.getWmSalesIssueLineList());
        return rows;
    }

    /**
     * 修改销售发料
     *
     * @param bo 销售发料业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmSalesIssueBo bo) {
        WmSalesIssue entity = MapstructUtils.convert(bo, WmSalesIssue.class);
        // ERP-WM-09: 仅草稿/已驳回可改；已确认/已完成出库单明细被"全删全插"后
        // 与已生成的 wm_material_stock_tx 不再对应，对账找不到差异来源
        WmSalesIssue existing = baseMapper.selectById(entity.getIssueId());
        if (existing == null) {
            throw new ServiceException("销售出库单不存在");
        }
        if (!STATUS_DRAFT.equals(existing.getStatus())
                && !STATUS_REJECTED.equals(existing.getStatus())) {
            throw new ServiceException("当前状态不允许修改，只有草稿和已驳回状态才能修改！");
        }
        entity.setStatus(existing.getStatus());
        lineMapper.delete(
                new LambdaQueryWrapper<WmSalesIssueLine>()
                        .eq(WmSalesIssueLine::getIssueId, entity.getIssueId()));
        insertLines(entity.getIssueId(), bo.getWmSalesIssueLineList());
        return baseMapper.updateById(entity);
    }

    /**
     * 删除销售发料
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            // ERP-WM-09: 仅草稿可删；已扣减库存的单据删除后流水成为孤儿、库存调整无凭据
            WmSalesIssue existing = baseMapper.selectById(id);
            if (existing != null && !STATUS_DRAFT.equals(existing.getStatus())) {
                throw new ServiceException("当前状态不允许删除，只有草稿状态才能删除！");
            }
        }
        lineMapper.delete(
                new LambdaQueryWrapper<WmSalesIssueLine>()
                        .in(WmSalesIssueLine::getIssueId, List.of(ids)));
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 销售发料处理（confirmSalesIssue）
     *
     * @param issueId 发料单 ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int confirmSalesIssue(Long issueId) {
        WmSalesIssue salesIssue = baseMapper.selectById(issueId);
        if (salesIssue == null) {
            throw new ServiceException("销售出库单不存在");
        }
        // ERP-WM-16: 只允许 APPROVED → CONFIRMED（DRAFT 不得绕过审批直接确认，而 CONFIRMED 是
        // completeSalesIssue 的唯一前置状态），并改为条件更新 CAS，防并发双击同时通过
        int rows =
                baseMapper.update(
                        null,
                        Wrappers.<WmSalesIssue>lambdaUpdate()
                                .set(WmSalesIssue::getStatus, STATUS_CONFIRMED)
                                .eq(WmSalesIssue::getIssueId, issueId)
                                .eq(WmSalesIssue::getStatus, STATUS_APPROVED));
        if (rows == 0) {
            throw new ServiceException("只有已批准(APPROVED)的出库单才能确认，或状态已被并发变更");
        }
        return rows;
    }

    /**
     * 销售发料处理（completeSalesIssue）
     *
     * @param issueId 发料单 ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int completeSalesIssue(Long issueId) {
        WmSalesIssue salesIssue = baseMapper.selectById(issueId);
        if (salesIssue == null) {
            throw new ServiceException("销售出库单不存在");
        }
        if (!STATUS_CONFIRMED.equals(salesIssue.getStatus())) {
            throw new ServiceException("只有已确认的出库单才能完成");
        }
        // 已有流水但单据尚未完成时明确拒绝对账，不认领状态、不自动补历史或再次扣库。
        Long txCount =
                stockTxMapper.selectCount(
                        Wrappers.<WmMaterialStockTx>lambdaQuery()
                                .eq(WmMaterialStockTx::getTenantId, salesIssue.getTenantId())
                                .eq(WmMaterialStockTx::getSourceDocType, "WM_SALES_ISSUE")
                                .eq(WmMaterialStockTx::getSourceDocId, issueId));
        if (txCount != null && txCount > 0) {
            throw new ServiceException("销售出库单已有库存流水但尚未完成，请先对账，禁止重复出库");
        }

        // 并发防重：条件更新抢占（CONFIRMED → EXECUTING），影响行数为 0 说明已被并发执行/已过期
        int claimed =
                baseMapper.update(
                        null,
                        Wrappers.<WmSalesIssue>lambdaUpdate()
                                .set(WmSalesIssue::getStatus, "EXECUTING")
                                .eq(WmSalesIssue::getIssueId, issueId)
                                .eq(WmSalesIssue::getStatus, STATUS_CONFIRMED));
        if (claimed == 0) {
            log.warn("销售出库单[{}]状态已变更，跳过重复处理", issueId);
            return 1;
        }

        List<WmSalesIssueLine> lineList =
                lineMapper.selectList(
                        new LambdaQueryWrapper<WmSalesIssueLine>()
                                .eq(WmSalesIssueLine::getIssueId, issueId));
        if (lineList == null || lineList.isEmpty()) {
            throw new ServiceException("出库单明细为空");
        }

        // 所有明细和关联来源必须先过门禁；原事务失败时会回滚上面的状态认领。
        Map<Long, SoSalesOrderLine> lockedSources =
                sourceGuard.validateAndLock(salesIssue, lineList);
        Date now = new Date();

        // 扣减库存：按批次效期、收货时间、库存ID排序，跨库存记录 FIFO 扣减
        for (WmSalesIssueLine line : lineList) {
            BigDecimal issueQty = line.getQuantityIssue();
            SoSalesOrderLine serialSource =
                    line.getSourceDocLineId() == null
                            ? null
                            : lockedSources.get(line.getSourceDocLineId());
            BigDecimal serialReserved =
                    serialSource == null || serialSource.getReservedQty() == null
                            ? BigDecimal.ZERO
                            : serialSource.getReservedQty();
            var serialRequest =
                    com.nexus.erp.wm.domain.WmSerialPostingRequest.out(
                                    "WM_SALES_ISSUE",
                                    issueId,
                                    line.getLineId(),
                                    "SALES_OUT",
                                    line.getMaterialId(),
                                    issueQty,
                                    salesIssue.getWarehouseId(),
                                    line.getLocationId(),
                                    line.getBatchId())
                            .reservation(
                                    salesIssue.getSoId(),
                                    line.getSourceDocLineId(),
                                    issueQty.min(serialReserved));
            if (serialInventory != null && serialInventory.post(serialRequest)) {
                BigDecimal consumed = serialInventory.consumedReservation(serialRequest);
                if (serialSource != null) {
                    serialSource.setReservedQty(serialReserved.subtract(consumed));
                }
                if (line.getSourceDocLineId() != null) {
                    fulfillmentWritebackService.addShippedQty(line.getSourceDocLineId(), issueQty);
                }
                continue;
            }

            LambdaQueryWrapper<WmMaterialStock> stockLqw = Wrappers.lambdaQuery();
            stockLqw.eq(WmMaterialStock::getTenantId, salesIssue.getTenantId());
            stockLqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
            stockLqw.eq(WmMaterialStock::getWarehouseId, salesIssue.getWarehouseId());
            stockLqw.eq(
                    line.getLocationId() != null,
                    WmMaterialStock::getLocationId,
                    line.getLocationId());
            stockLqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
            stockLqw.eq(WmMaterialStock::getFrozenFlag, 0L);
            stockLqw.gt(WmMaterialStock::getStockQuantity, BigDecimal.ZERO);
            // ERP-WM-06: 效期拦截 —— 已过期批次不得参与销售出库
            stockLqw.apply("(expire_date IS NULL OR expire_date >= CURDATE())");
            stockLqw.orderByAsc(WmMaterialStock::getExpireDate);
            stockLqw.orderByAsc(WmMaterialStock::getRecptDate);
            stockLqw.orderByAsc(WmMaterialStock::getStockId);

            List<WmMaterialStock> stockList = wmMaterialStockMapper.selectList(stockLqw);
            if (stockList == null || stockList.isEmpty()) {
                throw new ServiceException("物料【" + line.getMaterialName() + "】库存不存在");
            }

            BigDecimal remaining = issueQty;
            BigDecimal totalAvailable = BigDecimal.ZERO;
            for (WmMaterialStock stock : stockList) {
                totalAvailable =
                        totalAvailable.add(
                                stock.getStockQuantity() != null
                                        ? stock.getStockQuantity()
                                        : BigDecimal.ZERO);
            }
            if (totalAvailable.compareTo(issueQty) < 0) {
                throw new ServiceException(
                        "物料【"
                                + line.getMaterialName()
                                + "】库存不足，当前库存："
                                + totalAvailable
                                + "，出库数量："
                                + issueQty);
            }

            // 预留消耗: SO 行预留量随出库同步释放(与 SalesOutStrategy 口径一致)
            SoSalesOrderLine soLine =
                    line.getSourceDocLineId() == null
                            ? null
                            : lockedSources.get(line.getSourceDocLineId());
            BigDecimal soReservedQty =
                    soLine != null && soLine.getReservedQty() != null
                            ? soLine.getReservedQty()
                            : BigDecimal.ZERO;
            BigDecimal totalConsumeReserved = issueQty.min(soReservedQty);
            BigDecimal remainingConsumeReserved = totalConsumeReserved;

            for (WmMaterialStock stock : stockList) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                if (!Objects.equals(stock.getTenantId(), salesIssue.getTenantId())
                        || !Objects.equals(stock.getMaterialId(), line.getMaterialId())
                        || !Objects.equals(stock.getWarehouseId(), salesIssue.getWarehouseId())) {
                    throw new ServiceException("实际库存租户、物料或仓库与销售出库不一致");
                }
                BigDecimal currentQty =
                        stock.getStockQuantity() != null
                                ? stock.getStockQuantity()
                                : BigDecimal.ZERO;
                if (currentQty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal stockReserved =
                        stock.getReservedQuantity() == null
                                ? BigDecimal.ZERO
                                : stock.getReservedQuantity();
                // ERP-WM-02: 可动用上限 = 未预留可用量 + 本单尚可消耗的预留量（无 SO 行时严格不越过可用量）
                BigDecimal consumableReserved = stockReserved.min(remainingConsumeReserved);
                BigDecimal maxDeduct = currentQty.subtract(stockReserved).add(consumableReserved);
                if (maxDeduct.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal consumeQty = maxDeduct.min(remaining);
                // 本次扣减消费的预留量: min(本次扣减, 该库存行已预留, 剩余待消费预留)
                BigDecimal batchConsumeReserved =
                        consumeQty.min(stockReserved).min(remainingConsumeReserved);
                // 原子扣减库存（乐观锁 + 不允许负库存; 命中预留时同步释放预留, 未命中时按可用量守卫）
                int affected =
                        batchConsumeReserved.compareTo(BigDecimal.ZERO) > 0
                                ? wmMaterialStockMapper.deductStockWithReserved(
                                        stock.getStockId(),
                                        consumeQty,
                                        batchConsumeReserved,
                                        stock.getVersion())
                                : wmMaterialStockMapper.deductStockAvailable(
                                        stock.getStockId(), consumeQty, stock.getVersion());
                if (affected == 0) {
                    throw new ServiceException("库存并发冲突，请重试: 物料【" + line.getMaterialName() + "】");
                }
                remainingConsumeReserved = remainingConsumeReserved.subtract(batchConsumeReserved);

                // ERP-WM-01: 出库成本结转 —— 按当前移动平均单位成本扣减 wm_material_stock.total_cost。
                // completeSalesIssue 是销售出库的唯一活路径，修复前从不调用它（带结转的
                // postSalesIssue / SalesOutStrategy 全仓无调用方），导致库存数量减了而金额没减、
                // 出库成本恒为 0、库存金额虚高，下一次入库的移动平均价被高估，误差逐笔累积。
                // 与 SalesOutStrategy 完全同口径（内核批次已把该实现改为原子 SQL 相对扣减，无数量前置校验）。
                BigDecimal outUnitCost =
                        valuationService.issueStockCost(stock.getStockId(), consumeQty);

                // 按实际扣减的库存身份相对累计批次已发量，失败与扣库、成本共用原事务回滚。
                if (stock.getBatchId() != null
                        && batchMapper.increaseIssuedQty(
                                        stock.getBatchId(),
                                        stock.getMaterialId(),
                                        stock.getTenantId(),
                                        consumeQty)
                                != 1) {
                    throw new ServiceException("销售出库批次累计失败，批次身份不符或已发数量超过已收数量");
                }

                // ERP-WM-03: 预留消耗必须记账（与 SalesOutStrategy 口径一致），
                // 否则订单取消/关闭时 unreserve 会按历史 RESERVE 流水全额释放他人预留
                if (batchConsumeReserved.compareTo(BigDecimal.ZERO) > 0
                        && salesIssue.getSoId() != null) {
                    WmMaterialStockTx consumeTx = new WmMaterialStockTx();
                    consumeTx.setTenantId(stock.getTenantId());
                    consumeTx.setTxType("CONSUME_RESERVE");
                    consumeTx.setMaterialId(stock.getMaterialId());
                    consumeTx.setMaterialCode(stock.getMaterialCode());
                    consumeTx.setMaterialName(stock.getMaterialName());
                    consumeTx.setMaterialSpec(stock.getMaterialSpec());
                    consumeTx.setBaseUomId(stock.getBaseUomId());
                    consumeTx.setBaseUomName(stock.getBaseUomName());
                    consumeTx.setBatchId(stock.getBatchId());
                    consumeTx.setBatchCode(stock.getBatchCode());
                    consumeTx.setWarehouseId(stock.getWarehouseId());
                    consumeTx.setWarehouseCode(stock.getWarehouseCode());
                    consumeTx.setWarehouseName(stock.getWarehouseName());
                    consumeTx.setAreaId(stock.getAreaId());
                    consumeTx.setAreaCode(stock.getAreaCode());
                    consumeTx.setAreaName(stock.getAreaName());
                    consumeTx.setLocationId(stock.getLocationId());
                    consumeTx.setLocationCode(stock.getLocationCode());
                    consumeTx.setLocationName(stock.getLocationName());
                    consumeTx.setMaterialStockId(stock.getStockId());
                    consumeTx.setTxFlag(0);
                    consumeTx.setTxQuantity(batchConsumeReserved);
                    consumeTx.setBeforeQuantity(currentQty);
                    consumeTx.setAfterQuantity(currentQty);
                    consumeTx.setTxDate(now);
                    consumeTx.setSourceDocType("SALES_ORDER");
                    consumeTx.setSourceDocId(salesIssue.getSoId());
                    consumeTx.setSourceDocCode(salesIssue.getSoNo());
                    consumeTx.setSourceDocLineId(line.getSourceDocLineId());
                    consumeTx.setRemark("销售出库消耗预留，出库单号：" + salesIssue.getIssueCode());
                    if (stockTxMapper.insert(consumeTx) != 1) {
                        throw new ServiceException("销售出库预留消费流水写入失败");
                    }
                }

                // 生成库存事务记录
                WmMaterialStockTx tx = new WmMaterialStockTx();
                tx.setTenantId(stock.getTenantId());
                tx.setTxType("SALES_OUT");
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
                tx.setAreaId(stock.getAreaId());
                tx.setAreaCode(stock.getAreaCode());
                tx.setAreaName(stock.getAreaName());
                tx.setLocationId(stock.getLocationId());
                tx.setLocationCode(stock.getLocationCode());
                tx.setLocationName(stock.getLocationName());
                tx.setMaterialStockId(stock.getStockId());
                tx.setTxFlag(-1);
                tx.setTxQuantity(consumeQty);
                // ERP-WM-01: 出库成本随流水落账（修复前 unit_cost / tx_amount 恒为 null/0，无法核对出库成本）
                tx.setUnitCost(outUnitCost);
                tx.setTxAmount(outUnitCost.multiply(consumeQty).setScale(4, RoundingMode.HALF_UP));
                tx.setBeforeQuantity(currentQty);
                tx.setAfterQuantity(currentQty.subtract(consumeQty));
                tx.setTxDate(now);
                tx.setSourceDocType("WM_SALES_ISSUE");
                tx.setSourceDocId(issueId);
                tx.setSourceDocCode(salesIssue.getIssueCode());
                tx.setSourceDocLineId(line.getLineId());
                tx.setRemark("销售出库扣减库存，单号：" + salesIssue.getIssueCode());
                if (stockTxMapper.insert(tx) != 1) {
                    throw new ServiceException("销售出库库存流水写入失败");
                }

                remaining = remaining.subtract(consumeQty);
            }

            // ERP-WM-02: 扣减已按可用量守卫，若仍有缺口必须显式报错，
            // 不能像修复前那样静默按部分量出库（剩余量被无声丢弃，单据却标记 COMPLETED）
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                throw new ServiceException(
                        "物料【"
                                + line.getMaterialName()
                                + "】可用库存不足，尚缺："
                                + remaining.stripTrailingZeros().toPlainString());
            }

            // ERP-WM-15: SO 行预留递减量必须等于库存行"实际释放量"。
            // 修复前 SO 行按 totalConsumeReserved 递减、库存行按各行 batchConsumeReserved 之和释放，
            // 两者不等时差额永久残留为库存行 reserved_quantity 假占用（可用量虚减、ATP 与拣货都取不到该批次）。
            BigDecimal actualConsumeReserved =
                    totalConsumeReserved.subtract(remainingConsumeReserved);
            if (remainingConsumeReserved.compareTo(BigDecimal.ZERO) > 0) {
                log.warn(
                        "销售出库单[{}] 物料[{}] 预留释放口径不一致: SO行预留={}, 库存行实际释放={}, 按实际释放量递减 SO 行",
                        salesIssue.getIssueCode(),
                        line.getMaterialName(),
                        totalConsumeReserved,
                        actualConsumeReserved);
            }
            // 原子扣减 SO 行剩余预留数量(预留已随出库消耗, setSql 原子递减 + 预留充足守卫)
            if (actualConsumeReserved.compareTo(BigDecimal.ZERO) > 0 && soLine != null) {
                int reservedRows =
                        salesOrderLineMapper.update(
                                null,
                                Wrappers.<SoSalesOrderLine>lambdaUpdate()
                                        .eq(SoSalesOrderLine::getTenantId, salesIssue.getTenantId())
                                        .eq(SoSalesOrderLine::getSoId, salesIssue.getSoId())
                                        .eq(SoSalesOrderLine::getLineId, soLine.getLineId())
                                        .ge(SoSalesOrderLine::getReservedQty, actualConsumeReserved)
                                        .setSql(
                                                "reserved_qty = reserved_qty - "
                                                        + actualConsumeReserved.toPlainString()));
                if (reservedRows == 0) {
                    throw new ServiceException(
                            "SO行预留数量更新失败(并发冲突或预留不足), soLineId=" + soLine.getLineId());
                }
                // 同源多明细复用锁内行，并同步其剩余预留，防止后续明细重复消费旧预留量。
                soLine.setReservedQty(soReservedQty.subtract(actualConsumeReserved));
            }

            // 回写 SO 行已发货/已履行数量（销售出库即对该行的履行交付）
            if (line.getSourceDocLineId() != null && issueQty.compareTo(BigDecimal.ZERO) > 0) {
                fulfillmentWritebackService.addShippedQty(line.getSourceDocLineId(), issueQty);
            }
        }

        salesIssue.setStatus(STATUS_COMPLETED);
        return baseMapper.updateById(salesIssue);
    }

    // ==================== Flowable 工作流集成 ====================

    /**
     * 销售发料处理（submit）
     *
     * @param issueIdStr 参数 issueIdStr
     * @param selectFlowId selectFlow ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String issueIdStr, String selectFlowId) {
        Long issueId = Long.parseLong(issueIdStr);
        WmSalesIssue entity = baseMapper.selectById(issueId);
        if (entity == null) {
            throw new RuntimeException("销售出库单不存在");
        }
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new RuntimeException("只有草稿状态的出库单才能提交审批");
        }
        if (StringUtils.isBlank(selectFlowId)) {
            if (entity.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(entity.getSelectFlowId());
            } else {
                throw new RuntimeException("请先在编辑页选择审批流程并保存");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", issueIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("issueId", entity.getIssueId());
        variables.put("issueCode", entity.getIssueCode());
        variables.put("clientName", entity.getClientName());
        R<Void> result =
                wm.processService.startProcessByDataId(
                        issueIdStr, selectFlowId, "wmSalesIssueService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new RuntimeException(result.getMsg());
    }

    /**
     * 获取销售发料
     *
     * @param issueIdStr 参数 issueIdStr
     * @return 结果
     */
    @Override
    public String getProcInsId(String issueIdStr) {
        return wm.procInsIdHelper.resolve(
                issueIdStr,
                () -> {
                    WfMyBusiness business = wm.wfMyBusinessServiceImpl.getByDataId(issueIdStr);
                    return business != null ? business.getProcessInstanceId() : null;
                });
    }

    /**
     * 销售发料处理（afterFlowHandle）
     *
     * @param business 参数 business
     * @return 结果
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void afterFlowHandle(WfMyBusiness business) {
        Long issueId = Long.parseLong(business.getDataId());
        WmSalesIssue entity = baseMapper.selectById(issueId);
        if (entity == null) {
            return;
        }
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            entity.setStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            entity.setStatus(STATUS_APPROVED);
            // 审批通过后，事务提交后按需检物料发 OqcAutoCreateMessage 给 MES 自动创建 OQC
            registerOqcCreateMessages(entity);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            entity.setStatus(STATUS_REJECTED);
        } else if (ActStatus.revoke.equals(actStatus)
                || ActStatus.stop.equals(actStatus)
                || ActStatus.recall.equals(actStatus)) {
            entity.setStatus(STATUS_DRAFT);
        }
        baseMapper.updateById(entity);
    }

    /**
     * 注册 afterCommit 回调，事务提交后按"需检物料"逐条发 OqcAutoCreateMessage 给 MES 自动创建 OQC。
     * <p>
     * 触发点：销售出库审批通过(afterFlowHandle pass)。
     * 门控：出库行物料 inspection_required=1 才发；同 soId+materialId 去重(消费端亦幂等)。
     * soId 取自销售出库主表；物料/数量取自 wm_sales_issue_line。显式传 tenantId 避免消费端退化。
     *
     * @param entity 销售出库单
     */
    private void registerOqcCreateMessages(WmSalesIssue entity) {
        try {
            if (entity.getSoId() == null) {
                log.warn("销售出库 {} 无关联销售订单，跳过 OQC 自动创建", entity.getIssueId());
                return;
            }
            List<WmSalesIssueLine> lines =
                    lineMapper.selectList(
                            new LambdaQueryWrapper<WmSalesIssueLine>()
                                    .eq(WmSalesIssueLine::getIssueId, entity.getIssueId()));
            if (lines == null || lines.isEmpty()) {
                log.warn("销售出库 {} 无明细行，跳过 OQC 自动创建", entity.getIssueId());
                return;
            }
            // 批量查物料 inspection_required 门控
            Set<Long> materialIds = new HashSet<>();
            for (WmSalesIssueLine l : lines) {
                if (l.getMaterialId() != null) {
                    materialIds.add(l.getMaterialId());
                }
            }
            if (materialIds.isEmpty()) {
                return;
            }
            List<BdMaterial> materials =
                    bdMaterialMapper.selectList(
                            new LambdaQueryWrapper<BdMaterial>()
                                    .in(BdMaterial::getMaterialId, materialIds));
            Set<Long> needOqcMaterialIds = new HashSet<>();
            for (BdMaterial m : materials) {
                if (Integer.valueOf(1).equals(m.getInspectionRequired())) {
                    needOqcMaterialIds.add(m.getMaterialId());
                }
            }
            if (needOqcMaterialIds.isEmpty()) {
                log.info("销售出库 {} 所有出库物料均免检，跳过 OQC 自动创建", entity.getIssueId());
                return;
            }

            // 显式设置 tenantId，避免消费端退化为 000000
            long tenantIdVal = 0L;
            String tenantIdStr = TenantHelper.getTenantId();
            if (tenantIdStr != null) {
                try {
                    tenantIdVal = Long.parseLong(tenantIdStr);
                } catch (NumberFormatException ignore) {
                    tenantIdVal = 0L;
                }
            }
            final long tenantId = tenantIdVal;

            // 逐个需检物料构建消息（同物料去重，一物料一条 -> MES 建一条 OQC）
            List<OqcAutoCreateMessage> messages = new ArrayList<>();
            Set<Long> dispatched = new HashSet<>();
            for (WmSalesIssueLine l : lines) {
                if (l.getMaterialId() == null || !needOqcMaterialIds.contains(l.getMaterialId())) {
                    continue;
                }
                if (!dispatched.add(l.getMaterialId())) {
                    continue;
                }
                OqcAutoCreateMessage msg = new OqcAutoCreateMessage();
                msg.setSoId(entity.getSoId());
                msg.setMaterialId(l.getMaterialId());
                msg.setMaterialCode(l.getMaterialCode());
                msg.setMaterialName(l.getMaterialName());
                msg.setQuantity(l.getQuantityIssue());
                msg.setSourceModule("ERP");
                msg.setTenantId(tenantId);
                messages.add(msg);
            }
            if (messages.isEmpty()) {
                return;
            }
            final List<OqcAutoCreateMessage> toSend = messages;
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            for (OqcAutoCreateMessage msg : toSend) {
                                try {
                                    messageProducer.send(
                                            ExchangeConstants.TOPIC_EXCHANGE,
                                            RoutingKeyConstants.MES_OQC_CREATE,
                                            msg);
                                    log.info(
                                            "销售出库审批通过 OQC MQ 已发送: soId={}, materialId={}",
                                            entity.getSoId(),
                                            msg.getMaterialId());
                                } catch (Exception e) {
                                    log.error(
                                            "销售出库审批通过 OQC MQ 发送失败: soId={}, materialId={}",
                                            entity.getSoId(),
                                            msg.getMaterialId(),
                                            e);
                                }
                            }
                        }
                    });
        } catch (Exception e) {
            log.error("注册销售出库 OQC MQ 失败: issueId={}", entity.getIssueId(), e);
        }
    }

    /**
     * 获取销售发料详情
     *
     * @param dataId data ID
     * @return 结果
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Object getBusinessDataById(String dataId) {
        return selectById(Long.parseLong(dataId));
    }

    /**
     * 销售发料处理（flowValuesOfTask）
     *
     * @param taskNameId taskName ID
     * @param values 参数 values
     * @return 结果对象
     */
    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 销售发料处理（flowCandidateUsernamesOfTask）
     *
     * @param taskNameId taskName ID
     * @param values 参数 values
     * @return 结果对象
     */
    @Override
    public List<String> flowCandidateUsernamesOfTask(
            String taskNameId, Map<String, Object> values) {
        return null;
    }

    private void insertLines(Long issueId, List<WmSalesIssueLineBo> lineBoList) {
        if (lineBoList != null && !lineBoList.isEmpty()) {
            List<WmSalesIssueLine> lineList =
                    MapstructUtils.convert(lineBoList, WmSalesIssueLine.class);
            for (WmSalesIssueLine line : lineList) {
                line.setLineId(null);
                line.setIssueId(issueId);
            }
            lineMapper.insertBatch(lineList);
        }
    }

    private LambdaQueryWrapper<WmSalesIssue> buildQueryWrapper(WmSalesIssueBo bo) {
        LambdaQueryWrapper<WmSalesIssue> lqw = Wrappers.lambdaQuery();
        lqw.eq(
                StringUtils.isNotBlank(bo.getIssueCode()),
                WmSalesIssue::getIssueCode,
                bo.getIssueCode());
        lqw.like(
                StringUtils.isNotBlank(bo.getIssueName()),
                WmSalesIssue::getIssueName,
                bo.getIssueName());
        lqw.eq(bo.getSoId() != null, WmSalesIssue::getSoId, bo.getSoId());
        lqw.eq(StringUtils.isNotBlank(bo.getSoNo()), WmSalesIssue::getSoNo, bo.getSoNo());
        lqw.eq(bo.getClientId() != null, WmSalesIssue::getClientId, bo.getClientId());
        lqw.eq(
                StringUtils.isNotBlank(bo.getClientCode()),
                WmSalesIssue::getClientCode,
                bo.getClientCode());
        lqw.like(
                StringUtils.isNotBlank(bo.getClientName()),
                WmSalesIssue::getClientName,
                bo.getClientName());
        lqw.eq(bo.getWarehouseId() != null, WmSalesIssue::getWarehouseId, bo.getWarehouseId());
        lqw.eq(
                StringUtils.isNotBlank(bo.getWarehouseCode()),
                WmSalesIssue::getWarehouseCode,
                bo.getWarehouseCode());
        lqw.like(
                StringUtils.isNotBlank(bo.getWarehouseName()),
                WmSalesIssue::getWarehouseName,
                bo.getWarehouseName());
        lqw.eq(bo.getIssueDate() != null, WmSalesIssue::getIssueDate, bo.getIssueDate());
        lqw.eq(
                bo.getTotalQuantity() != null,
                WmSalesIssue::getTotalQuantity,
                bo.getTotalQuantity());
        lqw.eq(bo.getTotalAmount() != null, WmSalesIssue::getTotalAmount, bo.getTotalAmount());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmSalesIssue::getStatus, bo.getStatus());
        return lqw;
    }
}
