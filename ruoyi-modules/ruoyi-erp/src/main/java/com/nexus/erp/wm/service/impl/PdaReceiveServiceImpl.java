package com.nexus.erp.wm.service.impl;

import cn.hutool.core.date.DateUtil;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.tx.TransactionContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.wm.domain.WmArrivalNoticeLine;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.domain.WmMaterialReceiveDetail;
import com.nexus.erp.wm.domain.WmMaterialReceiveLine;
import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveBo;
import com.nexus.erp.wm.domain.bo.pda.PdaLineConfirmBo;
import com.nexus.erp.wm.domain.bo.pda.PdaShelfBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveVo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaReceiveDetailVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;
import com.nexus.erp.wm.mapper.WmArrivalNoticeLineMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveDetailMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveLineMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveMapper;
import com.nexus.erp.wm.mapper.WmStorageLocationMapper;
import com.nexus.erp.wm.service.IPdaReceiveService;
import com.nexus.erp.wm.service.IPutawayStrategyService;
import com.nexus.erp.wm.service.IWmMaterialReceiveCrudService;
import com.nexus.erp.wm.service.IWmMaterialReceiveService;
import com.nexus.erp.wm.service.PdaReceiveShelfAllocationService;
import com.nexus.erp.wm.service.WmCertifiedReceiptMutationGuard;
import com.nexus.erp.wm.service.WmCertifiedReceiptPostingService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PDA 收货作业服务实现 — 编排既有收货域服务, 不重建单据模型
 */
@DS("erp")
@Service
@RequiredArgsConstructor
public class PdaReceiveServiceImpl implements IPdaReceiveService {

    private final IWmMaterialReceiveService receiveService;
    private final WmMaterialReceiveMapper receiveMapper;
    private final WmCertifiedReceiptMutationGuard certifiedReceiptGuard;
    private final WmCertifiedReceiptPostingService certifiedPosting;
    private final PdaReceiveShelfAllocationService shelfAllocation;
    private final WmMaterialReceiveLineMapper lineMapper;

    /** 到货通知行 Mapper（ERP-WM-03: 超收上限取自到货通知行应到量 quantity_arrival） */
    private final WmArrivalNoticeLineMapper arrivalNoticeLineMapper;

    private final WmMaterialReceiveDetailMapper detailMapper;
    private final WmStorageLocationMapper locationMapper;
    private final IPutawayStrategyService putawayStrategyService;

    @Override
    public TableDataInfo<PdaTodoItemVo> todo(String keyword, PageQuery pageQuery) {
        WmMaterialReceiveBo bo = new WmMaterialReceiveBo();
        if (StringUtils.isNotBlank(keyword)) {
            bo.setReceiveCode(keyword);
        }
        TableDataInfo<WmMaterialReceiveVo> page = receiveService.selectPageList(bo, pageQuery);
        List<PdaTodoItemVo> items = new ArrayList<>(page.getRows().size());
        for (WmMaterialReceiveVo vo : page.getRows()) {
            PdaTodoItemVo item = new PdaTodoItemVo();
            item.setId(vo.getReceiveId());
            item.setCode(vo.getReceiveCode());
            item.setStatus(vo.getStatus());
            item.setName(vo.getReceiveName());
            item.setWarehouseName(vo.getWarehouseName());
            item.setDate(vo.getReceiveDate() != null ? DateUtil.formatDate(vo.getReceiveDate()) : null);
            items.add(item);
        }
        return new TableDataInfo<>(items, page.getTotal());
    }

    @Override
    public PdaReceiveDetailVo detail(Long receiveId) {
        WmMaterialReceiveVo head = receiveService.selectById(receiveId);
        if (head == null) {
            throw new ServiceException("收货单不存在");
        }
        PdaReceiveDetailVo vo = new PdaReceiveDetailVo();
        vo.setReceiveId(head.getReceiveId());
        vo.setReceiveCode(head.getReceiveCode());
        vo.setStatus(head.getStatus());
        vo.setVendorName(head.getVendorName());
        vo.setWarehouseId(head.getWarehouseId());
        vo.setWarehouseName(head.getWarehouseName());
        vo.setReceiveDate(head.getReceiveDate() != null ? DateUtil.formatDate(head.getReceiveDate()) : null);
        vo.setIqcCode(head.getIqcCode());
        vo.setRemark(head.getRemark());

        List<WmMaterialReceiveLine> lines = lineMapper.selectList(
            Wrappers.<WmMaterialReceiveLine>lambdaQuery().eq(WmMaterialReceiveLine::getReceiveId, receiveId));
        List<WmMaterialReceiveDetail> details = detailMapper.selectList(
            Wrappers.<WmMaterialReceiveDetail>lambdaQuery().eq(WmMaterialReceiveDetail::getReceiveId, receiveId));
        Map<Long, String> lineLocation = new HashMap<>();
        for (WmMaterialReceiveDetail d : details) {
            lineLocation.putIfAbsent(d.getLineId(), d.getLocationCode());
        }

        boolean iqcRequired = head.getIqcId() != null;
        List<PdaReceiveDetailVo.PdaReceiveLineVo> lineVos = new ArrayList<>(lines.size());
        for (WmMaterialReceiveLine line : lines) {
            PdaReceiveDetailVo.PdaReceiveLineVo lv = new PdaReceiveDetailVo.PdaReceiveLineVo();
            lv.setLineId(line.getLineId());
            lv.setMaterialId(line.getMaterialId());
            lv.setMaterialCode(line.getMaterialCode());
            lv.setMaterialName(line.getMaterialName());
            lv.setMaterialSpec(line.getMaterialSpec());
            lv.setBaseUomName(line.getBaseUomName());
            lv.setBatchCode(line.getBatchCode());
            lv.setQtyReceived(line.getQuantityReceived());
            // 应收数量来源到货通知行, M1 暂不联动, 返回 null
            lv.setQtyExpected(null);
            lv.setLocationCode(lineLocation.get(line.getLineId()));
            lv.setIqcRequired(iqcRequired);
            lineVos.add(lv);
        }
        vo.setLines(lineVos);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdaActionResultVo confirmLine(Long receiveId, PdaLineConfirmBo bo) {
        // 必须参与当前 ERP 事务：先锁真实头/行归属并拒绝已出生或已过账来源，再读量、写量。
        certifiedReceiptGuard.requireMutableLine(bo.getLineId(), receiveId);
        WmMaterialReceiveVo head = currentHead(receiveId);
        if (head == null) {
            throw new ServiceException("收货单不存在");
        }
        if (!IWmMaterialReceiveCrudService.STATUS_APPROVED.equals(head.getStatus())) {
            return PdaActionResultVo.fail("单据当前状态不允许收货核销(需已审批待上架)");
        }
        WmMaterialReceiveLine line = lineMapper.selectOne(Wrappers.<WmMaterialReceiveLine>lambdaQuery()
            .eq(WmMaterialReceiveLine::getLineId, bo.getLineId())
            .eq(WmMaterialReceiveLine::getReceiveId, receiveId).last("FOR UPDATE"));
        if (line == null) {
            return PdaActionResultVo.fail("单据行不存在");
        }

        // 库位校验(可选): 不属于本仓库时返回推荐库位
        Long locationId = null;
        String locationCode = null;
        if (StringUtils.isNotBlank(bo.getLocationCode())) {
            WmStorageLocation loc = findLocation(head.getWarehouseId(), bo.getLocationCode());
            if (loc == null) {
                return recommendFail(line.getMaterialId(), head.getWarehouseId(), bo.getQty());
            }
            locationId = loc.getLocationId();
            locationCode = loc.getLocationCode();
        }

        if (bo.getQty() == null || bo.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            return PdaActionResultVo.fail("本次实收数量必须大于0");
        }

        // ERP-WM-03: 超收上限 + 原子累加。
        // 修复前 quantity_received 与上架明细 quantity 都是"读-改-写、无任何上限"的累加，
        // 同一行重复扫码 / 客户端重放 / 断网重试即可超收入库（账面库存 > 实际到货，多出的库存可被后续消耗）。
        // 上限取到货通知行"应到量"（quantity_arrival 按物料汇总），与 PdaReturnServiceImpl:114 的超退拦截同型。
        BigDecimal limit = resolveArrivalLimit(head.getNoticeId(), line.getMaterialId());
        BigDecimal received = line.getQuantityReceived() == null ? BigDecimal.ZERO : line.getQuantityReceived();
        BigDecimal newReceived = received.add(bo.getQty());
        if (limit != null && newReceived.compareTo(limit) > 0) {
            return PdaActionResultVo.fail("累计实收(" + newReceived.stripTrailingZeros().toPlainString()
                + ")超过应到数量(" + limit.stripTrailingZeros().toPlainString() + ")");
        }
        // 分配明细也先核当前归属；不能先改行再发现明细重绑，且锁定读不能复用旧 RR 数量。
        WmMaterialReceiveDetail detail = detailMapper.selectOne(Wrappers.<WmMaterialReceiveDetail>lambdaQuery()
            .eq(WmMaterialReceiveDetail::getLineId, bo.getLineId())
            .eq(WmMaterialReceiveDetail::getReceiveId, receiveId).last("FOR UPDATE"));
        if (detail == null) {
            certifiedReceiptGuard.requireMutableDetailParent(receiveId, line.getLineId());
        } else {
            certifiedReceiptGuard.requireMutableDetail(detail.getDetailId(), receiveId, line.getLineId());
        }

        // 条件更新：数量原子累加 + 上限守卫（影响 0 行 = 并发下已被推到超限，或行已被删除）
        int updated = lineMapper.update(null, Wrappers.<WmMaterialReceiveLine>lambdaUpdate()
            .setSql("quantity_received = IFNULL(quantity_received, 0) + " + bo.getQty().toPlainString())
            .set(StringUtils.isNotBlank(bo.getBatchCode()), WmMaterialReceiveLine::getBatchCode, bo.getBatchCode())
            .eq(WmMaterialReceiveLine::getLineId, bo.getLineId())
            .eq(WmMaterialReceiveLine::getReceiveId, receiveId)
            .apply(limit != null, "IFNULL(quantity_received, 0) + {0} <= {1}", bo.getQty(), limit));
        if (updated == 0) {
            return PdaActionResultVo.fail("累计实收超过应到数量或并发冲突，请刷新后重试");
        }

        // 上架明细: 每行一条, 数量跟随行实收总数(不再独立无上限累加), 库位覆盖

        if (detail == null) {
            detail = new WmMaterialReceiveDetail();
            detail.setLineId(line.getLineId());
            detail.setReceiveId(receiveId);
            detail.setMaterialId(line.getMaterialId());
            detail.setMaterialCode(line.getMaterialCode());
            detail.setMaterialName(line.getMaterialName());
            detail.setMaterialSpec(line.getMaterialSpec());
            detail.setBaseUomId(line.getBaseUomId());
            detail.setBaseUomName(line.getBaseUomName());
            detail.setBatchId(line.getBatchId());
            detail.setBatchCode(line.getBatchCode());
            detail.setWarehouseId(line.getWarehouseId());
            detail.setWarehouseCode(line.getWarehouseCode());
            detail.setWarehouseName(line.getWarehouseName());
            detail.setAreaId(line.getAreaId());
            detail.setAreaCode(line.getAreaCode());
            detail.setAreaName(line.getAreaName());
            detail.setQuantity(newReceived);
            detail.setLocationId(locationId);
            detail.setLocationCode(locationCode);
            detailMapper.insert(detail);
        } else {
            // ERP-WM-03: 明细数量与行实收总数保持一致，消除第二条无上限累加路径
            detail.setQuantity(newReceived);
            if (locationId != null) {
                detail.setLocationId(locationId);
                detail.setLocationCode(locationCode);
            }
            if (StringUtils.isNotBlank(bo.getBatchCode())) {
                detail.setBatchCode(bo.getBatchCode());
            }
            detailMapper.updateById(detail);
        }
        return PdaActionResultVo.ok(PdaActionResultVo.NEXT_SCAN_MATERIAL);
    }

    @Override
    public PdaActionResultVo shelf(Long receiveId, PdaShelfBo bo) {
        // 分类及专属上架都不能继承普通分配事务；也不静默挂起调用方事务。
        if (TransactionSynchronizationManager.isActualTransactionActive()
            || TransactionSynchronizationManager.isSynchronizationActive()
            || !TransactionSynchronizationManager.getResourceMap().isEmpty()
            || TransactionSynchronizationManager.isCurrentTransactionReadOnly() || TransactionContext.getXID() != null) {
            throw new ServiceException("PDA上架调度必须在无既有事务上下文中执行");
        }
        if (!certifiedPosting.handlesShelf(receiveId)) return shelfAllocation.shelfLegacy(receiveId, bo);
        PdaActionResultVo allocated = shelfAllocation.allocateCertified(receiveId, bo);
        if (allocated == null) throw new ServiceException("仓储分配结果不可用");
        if (!Boolean.TRUE.equals(allocated.getOk())) return allocated;
        // 实际代理返回后分配已经提交；认证上架另启专属 RR，重新核所有原生来源与真实 V2。
        // 两个事务不具整体原子性：上架失败不撤销已提交分配，不标记单据完成，原分配可重核重试。
        try {
            if (certifiedPosting.shelf(receiveId)) return PdaActionResultVo.ok(PdaActionResultVo.NEXT_FINISHED);
        } catch (RuntimeException unprovenShelf) {
            // 不把 SQL、连接诊断或来源主体返回 PDA；不得用失败兜底进入旧上架或物理入库。
        }
        return PdaActionResultVo.fail("仓储分配已提交，认证上架未完成；请核验当前单据后重试");
    }
    @Override
    public PdaActionResultVo finish(Long receiveId) {
        WmMaterialReceiveVo head = receiveService.selectById(receiveId);
        if (head == null) {
            throw new ServiceException("收货单不存在");
        }
        if (!IWmMaterialReceiveCrudService.STATUS_SHELVED.equals(head.getStatus())) {
            return PdaActionResultVo.fail("请先完成上架确认");
        }
        receiveService.executeReceive(receiveId);
        return PdaActionResultVo.ok(PdaActionResultVo.NEXT_FINISHED);
    }

    /** 写路径使用当前锁定读；普通快照不能覆盖守卫已取得的真实头状态或源量。 */
    private WmMaterialReceiveVo currentHead(Long receiveId) {
        return receiveMapper.selectVoOne(Wrappers.<WmMaterialReceive>lambdaQuery()
            .eq(WmMaterialReceive::getReceiveId, receiveId)
            .eq(WmMaterialReceive::getTenantId, TenantHelper.getTenantId()).last("FOR UPDATE"));
    }
    /**
     * ERP-WM-03: 解析行的"应到量"上限 —— 取到货通知行 {@code quantity_arrival} 按物料汇总。
     * <p>无到货通知关联（手工建单）时返回 {@code null} 表示无系统上限：
     * 这类单据在 wm 域内没有可比的"应收"参考量（{@code wm_material_receive_line} 只有实收数量列），
     * 需接入采购订单行应订量后才能补齐，见修复报告 §2 未修复项。</p>
     *
     * @param noticeId   到货通知单 ID（可空）
     * @param materialId 物料 ID（可空）
     * @return 应到量上限；{@code null} 表示不限
     */

    private BigDecimal resolveArrivalLimit(Long noticeId, Long materialId) {
        if (noticeId == null || materialId == null) {
            return null;
        }
        List<WmArrivalNoticeLine> noticeLines =
                arrivalNoticeLineMapper.selectList(
                        Wrappers.<WmArrivalNoticeLine>lambdaQuery()
                                .eq(WmArrivalNoticeLine::getNoticeId, noticeId)
                                .eq(WmArrivalNoticeLine::getMaterialId, materialId));
        BigDecimal limit = null;
        for (WmArrivalNoticeLine nl : noticeLines) {
            if (nl.getQuantityArrival() != null) {
                limit = (limit == null ? BigDecimal.ZERO : limit).add(nl.getQuantityArrival());
            }
        }
        return limit;
    }

    private WmStorageLocation findLocation(Long warehouseId, String locationCode) {
        if (StringUtils.isBlank(locationCode)) {
            return null;
        }
        return locationMapper.selectOne(Wrappers.<WmStorageLocation>lambdaQuery()
            .eq(WmStorageLocation::getLocationCode, locationCode)
            .eq(WmStorageLocation::getWarehouseId, warehouseId)
            .last("limit 1"));
    }

    private PdaActionResultVo recommendFail(Long materialId, Long warehouseId, BigDecimal qty) {
        PdaActionResultVo result = PdaActionResultVo.fail("库位不符或不存在");
        try {
            if (materialId != null && warehouseId != null) {
                WmStorageLocation rec = putawayStrategyService.recommendLocation(materialId, warehouseId, qty);
                if (rec != null) {
                    result.setRecommendedLocation(rec.getLocationCode());
                }
            }
        } catch (Exception ignored) {
            // 推荐失败不影响主流程错误提示
        }
        return result;
    }
}
