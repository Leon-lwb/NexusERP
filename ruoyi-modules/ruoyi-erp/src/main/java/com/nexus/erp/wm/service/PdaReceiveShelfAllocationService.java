package com.nexus.erp.wm.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.tx.TransactionContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.domain.WmMaterialReceiveDetail;
import com.nexus.erp.wm.domain.WmMaterialReceiveLine;
import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.bo.pda.PdaShelfBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveVo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.mapper.WmMaterialReceiveDetailMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveLineMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveMapper;
import com.nexus.erp.wm.mapper.WmStorageLocationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PDA 仓储分配的普通 ERP 事务入口。认证分配先独立提交，再由外层进入专属上架事务；
 * 旧收货分配与旧 CRUD 上架在同一事务中完成。没有 facade 依赖，不重新分类或调用专属过账。
 */
@Service
@DS("erp")
@RequiredArgsConstructor
public class PdaReceiveShelfAllocationService {
    private final WmMaterialReceiveMapper receiveMapper;
    private final WmMaterialReceiveLineMapper lineMapper;
    private final WmMaterialReceiveDetailMapper detailMapper;
    private final WmStorageLocationMapper locationMapper;
    private final IPutawayStrategyService putawayStrategyService;
    private final WmCertifiedReceiptMutationGuard certifiedReceiptGuard;
    private final IWmMaterialReceiveCrudService crudService;

    /** 必须经实际 Spring 代理调用；返回成功时仅代表分配事务已提交，不代表认证上架通过。 */
    @Transactional(rollbackFor = Exception.class)
    public PdaActionResultVo allocateCertified(Long receiveId, PdaShelfBo bo) {
        return allocate(receiveId, bo);
    }

    /** 旧质检或上架失败必须回滚本次分配，不能从普通事务中再次进入 facade 的专属分类。 */
    @Transactional(rollbackFor = Exception.class)
    public PdaActionResultVo shelfLegacy(Long receiveId, PdaShelfBo bo) {
        // 外层分类不是写授权：同事务头锁下再次禁止已出生、已过账或已变为认证采购的旧来源。
        // 这里只复用原生守卫，不再次调用 facade 或进入专属分类事务。
        certifiedReceiptGuard.requireLegacyExecution(receiveId);
        PdaActionResultVo result = allocate(receiveId, bo);
        if (!Boolean.TRUE.equals(result.getOk())) return result;
        if (!crudService.confirmShelf(receiveId)) throw new ServiceException("旧收货上架确认未成功，本次分配回滚");
        return PdaActionResultVo.ok(PdaActionResultVo.NEXT_FINISHED);
    }

    private PdaActionResultVo allocate(Long receiveId, PdaShelfBo bo) {
        String tenant = TenantHelper.getTenantId();
        if (!TransactionSynchronizationManager.isActualTransactionActive()
            || TransactionSynchronizationManager.isCurrentTransactionReadOnly() || TransactionContext.getXID() != null
            || !TenantHelper.isEnable() || tenant == null || !tenant.matches("[0-9]{6,20}")) {
            throw new ServiceException("PDA仓储分配必须参与当前租户的普通ERP事务");
        }
        // 当前头锁串行化来源变更、分配及上架；不能把旧 RR 快照当作当前批准状态。
        WmMaterialReceiveVo head = receiveMapper.selectVoOne(Wrappers.<WmMaterialReceive>lambdaQuery()
            .eq(WmMaterialReceive::getReceiveId, receiveId).eq(WmMaterialReceive::getTenantId, tenant).last("FOR UPDATE"));
        if (head == null) throw new ServiceException("收货单不存在");
        if (!IWmMaterialReceiveCrudService.STATUS_APPROVED.equals(head.getStatus())) {
            return PdaActionResultVo.fail("单据当前状态不允许上架确认");
        }
        WmStorageLocation location = locationMapper.selectOne(Wrappers.<WmStorageLocation>lambdaQuery()
            .eq(WmStorageLocation::getLocationCode, bo.getLocationCode())
            .eq(WmStorageLocation::getWarehouseId, head.getWarehouseId()).eq(WmStorageLocation::getTenantId, tenant));
        if (location == null) return recommendFail(head, receiveId);

        List<WmMaterialReceiveLine> lines = lineMapper.selectList(Wrappers.<WmMaterialReceiveLine>lambdaQuery()
            .eq(WmMaterialReceiveLine::getReceiveId, receiveId).eq(WmMaterialReceiveLine::getTenantId, tenant)
            .orderByAsc(WmMaterialReceiveLine::getLineId).last("FOR UPDATE"));
        if (lines == null) throw new ServiceException("收货来源行读取失败");
        if (lines.isEmpty()) return PdaActionResultVo.fail("单据无明细行");
        List<WmMaterialReceiveDetail> details = detailMapper.selectList(Wrappers.<WmMaterialReceiveDetail>lambdaQuery()
            .eq(WmMaterialReceiveDetail::getReceiveId, receiveId).eq(WmMaterialReceiveDetail::getTenantId, tenant)
            .orderByAsc(WmMaterialReceiveDetail::getDetailId).last("FOR UPDATE"));
        if (details == null) throw new ServiceException("收货分配明细读取失败");
        Map<Long, List<WmMaterialReceiveDetail>> byLine = new LinkedHashMap<>();
        // 全部归属先核完再写，拒绝跨头/跨行分配；出生绑定不认证未来仓储维度。
        for (WmMaterialReceiveLine line : lines) {
            if (line == null || byLine.putIfAbsent(line.getLineId(), new ArrayList<>()) != null) {
                throw new ServiceException("收货来源行身份冲突");
            }
            certifiedReceiptGuard.requireMutableDetailParent(receiveId, line.getLineId());
        }
        for (WmMaterialReceiveDetail detail : details) {
            if (detail == null || !byLine.containsKey(detail.getLineId())) throw new ServiceException("分配明细原行归属未知");
            certifiedReceiptGuard.requireMutableDetail(detail.getDetailId(), receiveId, detail.getLineId());
            byLine.get(detail.getLineId()).add(detail);
        }
        for (WmMaterialReceiveLine line : lines) {
            List<WmMaterialReceiveDetail> allocated = byLine.get(line.getLineId());
            if (allocated.isEmpty()) {
                WmMaterialReceiveDetail detail = allocationFrom(line, receiveId, tenant, location);
                one(detailMapper.insert(detail));
            } else {
                for (WmMaterialReceiveDetail detail : allocated) {
                    if (detail.getLocationId() != null) continue;
                    // 只更新未定位明细的库位列，不用整实体写回原数量、原单位或来源父身份。
                    one(detailMapper.update(null, Wrappers.<WmMaterialReceiveDetail>lambdaUpdate()
                        .set(WmMaterialReceiveDetail::getLocationId, location.getLocationId())
                        .set(WmMaterialReceiveDetail::getLocationCode, location.getLocationCode())
                        .eq(WmMaterialReceiveDetail::getDetailId, detail.getDetailId())
                        .eq(WmMaterialReceiveDetail::getReceiveId, receiveId).eq(WmMaterialReceiveDetail::getLineId, line.getLineId())
                        .eq(WmMaterialReceiveDetail::getTenantId, tenant).isNull(WmMaterialReceiveDetail::getLocationId)));
                }
            }
        }
        return PdaActionResultVo.ok();
    }

    /** 仅新建仓储分配引用当前锁定行的原数量；从不改行实收，也不给来源补认证。 */
    private WmMaterialReceiveDetail allocationFrom(WmMaterialReceiveLine line, Long receiveId,
        String tenant, WmStorageLocation location) {
        if (line.getQuantityReceived() == null || line.getQuantityReceived().signum() <= 0) {
            throw new ServiceException("实际收货数量缺失，不能生成仓储分配");
        }
        WmMaterialReceiveDetail detail = new WmMaterialReceiveDetail();
        detail.setTenantId(tenant); detail.setLineId(line.getLineId()); detail.setReceiveId(receiveId);
        detail.setMaterialId(line.getMaterialId()); detail.setMaterialCode(line.getMaterialCode());
        detail.setMaterialName(line.getMaterialName()); detail.setMaterialSpec(line.getMaterialSpec());
        detail.setBaseUomId(line.getBaseUomId()); detail.setBaseUomName(line.getBaseUomName());
        detail.setBatchId(line.getBatchId()); detail.setBatchCode(line.getBatchCode());
        detail.setWarehouseId(line.getWarehouseId()); detail.setWarehouseCode(line.getWarehouseCode());
        detail.setWarehouseName(line.getWarehouseName()); detail.setAreaId(line.getAreaId());
        detail.setAreaCode(line.getAreaCode()); detail.setAreaName(line.getAreaName());
        detail.setQuantity(line.getQuantityReceived()); detail.setLocationId(location.getLocationId());
        detail.setLocationCode(location.getLocationCode());
        return detail;
    }

    private PdaActionResultVo recommendFail(WmMaterialReceiveVo head, Long receiveId) {
        PdaActionResultVo result = PdaActionResultVo.fail("库位不符或不存在");
        WmMaterialReceiveLine line = lineMapper.selectOne(Wrappers.<WmMaterialReceiveLine>lambdaQuery()
            .eq(WmMaterialReceiveLine::getReceiveId, receiveId).last("limit 1"));
        try {
            if (line != null && line.getMaterialId() != null && head.getWarehouseId() != null) {
                WmStorageLocation recommended = putawayStrategyService.recommendLocation(line.getMaterialId(), head.getWarehouseId(), BigDecimal.ONE);
                if (recommended != null) result.setRecommendedLocation(recommended.getLocationCode());
            }
        } catch (RuntimeException unavailable) {
            // 推荐仅是只读辅助，失败不允许分配或上架，也不暴露原生诊断。
        }
        return result;
    }

    private static void one(int rows) {
        if (rows != 1) throw new ServiceException("仓储分配写入冲突，本次分配回滚");
    }
}