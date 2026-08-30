package com.nexus.erp.wm.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
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
import com.nexus.erp.wm.mapper.WmMaterialReceiveDetailMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveLineMapper;
import com.nexus.erp.wm.mapper.WmStorageLocationMapper;
import com.nexus.erp.wm.service.IPdaReceiveService;
import com.nexus.erp.wm.service.IPutawayStrategyService;
import com.nexus.erp.wm.service.IWmMaterialReceiveCrudService;
import com.nexus.erp.wm.service.IWmMaterialReceiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final WmMaterialReceiveLineMapper lineMapper;
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
        WmMaterialReceiveVo head = receiveService.selectById(receiveId);
        if (head == null) {
            throw new ServiceException("收货单不存在");
        }
        if (!IWmMaterialReceiveCrudService.STATUS_APPROVED.equals(head.getStatus())) {
            return PdaActionResultVo.fail("单据当前状态不允许收货核销(需已审批待上架)");
        }
        WmMaterialReceiveLine line = lineMapper.selectOne(Wrappers.<WmMaterialReceiveLine>lambdaQuery()
            .eq(WmMaterialReceiveLine::getLineId, bo.getLineId())
            .eq(WmMaterialReceiveLine::getReceiveId, receiveId));
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

        // 行实收数量累计 + 批次回填
        BigDecimal received = line.getQuantityReceived() == null ? BigDecimal.ZERO : line.getQuantityReceived();
        line.setQuantityReceived(received.add(bo.getQty()));
        if (StringUtils.isNotBlank(bo.getBatchCode())) {
            line.setBatchCode(bo.getBatchCode());
        }
        lineMapper.updateById(line);

        // 上架明细: 每行一条, 数量累计, 库位覆盖
        WmMaterialReceiveDetail detail = detailMapper.selectOne(Wrappers.<WmMaterialReceiveDetail>lambdaQuery()
            .eq(WmMaterialReceiveDetail::getLineId, bo.getLineId())
            .eq(WmMaterialReceiveDetail::getReceiveId, receiveId)
            .last("limit 1"));
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
            detail.setQuantity(bo.getQty());
            detail.setLocationId(locationId);
            detail.setLocationCode(locationCode);
            detailMapper.insert(detail);
        } else {
            BigDecimal qty = detail.getQuantity() == null ? BigDecimal.ZERO : detail.getQuantity();
            detail.setQuantity(qty.add(bo.getQty()));
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
    @Transactional(rollbackFor = Exception.class)
    public PdaActionResultVo shelf(Long receiveId, PdaShelfBo bo) {
        WmMaterialReceiveVo head = receiveService.selectById(receiveId);
        if (head == null) {
            throw new ServiceException("收货单不存在");
        }
        if (!IWmMaterialReceiveCrudService.STATUS_APPROVED.equals(head.getStatus())) {
            return PdaActionResultVo.fail("单据当前状态不允许上架确认");
        }
        WmStorageLocation loc = findLocation(head.getWarehouseId(), bo.getLocationCode());
        if (loc == null) {
            WmMaterialReceiveLine firstLine = lineMapper.selectOne(Wrappers.<WmMaterialReceiveLine>lambdaQuery()
                .eq(WmMaterialReceiveLine::getReceiveId, receiveId).last("limit 1"));
            Long materialId = firstLine != null ? firstLine.getMaterialId() : null;
            return recommendFail(materialId, head.getWarehouseId(), BigDecimal.ONE);
        }

        List<WmMaterialReceiveLine> lines = lineMapper.selectList(
            Wrappers.<WmMaterialReceiveLine>lambdaQuery().eq(WmMaterialReceiveLine::getReceiveId, receiveId));
        if (lines.isEmpty()) {
            return PdaActionResultVo.fail("单据无明细行");
        }
        List<WmMaterialReceiveDetail> details = detailMapper.selectList(
            Wrappers.<WmMaterialReceiveDetail>lambdaQuery().eq(WmMaterialReceiveDetail::getReceiveId, receiveId));
        Map<Long, WmMaterialReceiveDetail> detailByLine = new HashMap<>();
        for (WmMaterialReceiveDetail d : details) {
            detailByLine.putIfAbsent(d.getLineId(), d);
        }

        // 每行必须有已定位的上架明细: 缺失则按行实收数量补建, 未定位则填充本次库位
        for (WmMaterialReceiveLine line : lines) {
            WmMaterialReceiveDetail detail = detailByLine.get(line.getLineId());
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
                detail.setQuantity(line.getQuantityReceived() == null ? BigDecimal.ZERO : line.getQuantityReceived());
                detail.setLocationId(loc.getLocationId());
                detail.setLocationCode(loc.getLocationCode());
                detailMapper.insert(detail);
            } else if (detail.getLocationId() == null) {
                detail.setLocationId(loc.getLocationId());
                detail.setLocationCode(loc.getLocationCode());
                detailMapper.updateById(detail);
            }
        }

        // 复用既有上架确认(含质检联动校验)
        receiveService.confirmShelf(receiveId);
        return PdaActionResultVo.ok(PdaActionResultVo.NEXT_FINISHED);
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
