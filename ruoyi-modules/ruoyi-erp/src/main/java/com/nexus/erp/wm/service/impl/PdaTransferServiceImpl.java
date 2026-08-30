package com.nexus.erp.wm.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmTransfer;
import com.nexus.erp.wm.domain.WmTransferLine;
import com.nexus.erp.wm.domain.bo.WmTransferBo;
import com.nexus.erp.wm.domain.bo.pda.PdaShelfBo;
import com.nexus.erp.wm.domain.bo.pda.PdaTransferTakeoffBo;
import com.nexus.erp.wm.domain.vo.WmTransferVo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTransferDetailVo;
import com.nexus.erp.wm.mapper.WmTransferLineMapper;
import com.nexus.erp.wm.mapper.WmTransferMapper;
import com.nexus.erp.wm.service.IPdaTransferService;
import com.nexus.erp.wm.service.IWmTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * PDA 移库作业实现 — 两段式: 行级下架核销(扫源库位) → 上架确认(扫目标库位)过账
 */
@DS("erp")
@Service
@RequiredArgsConstructor
public class PdaTransferServiceImpl implements IPdaTransferService {

    private final IWmTransferService transferService;
    private final WmTransferLineMapper lineMapper;
    private final WmTransferMapper transferMapper;

    @Override
    public TableDataInfo<PdaTodoItemVo> todo(String keyword, PageQuery pageQuery) {
        WmTransferBo bo = new WmTransferBo();
        if (StringUtils.isNotBlank(keyword)) {
            bo.setTransferNo(keyword);
        }
        TableDataInfo<WmTransferVo> page = transferService.selectPageList(bo, pageQuery);
        List<PdaTodoItemVo> items = new ArrayList<>(page.getRows().size());
        for (WmTransferVo vo : page.getRows()) {
            PdaTodoItemVo item = new PdaTodoItemVo();
            item.setId(vo.getTransferId());
            item.setCode(vo.getTransferNo());
            item.setStatus(vo.getStatus());
            item.setName(vo.getFromWarehouseName() + " → " + vo.getToWarehouseName());
            item.setWarehouseName(vo.getFromWarehouseName());
            item.setDate(vo.getTransferDate() != null ? DateUtil.formatDate(vo.getTransferDate()) : null);
            items.add(item);
        }
        return new TableDataInfo<>(items, page.getTotal());
    }

    @Override
    public PdaTransferDetailVo detail(Long transferId) {
        WmTransferVo head = transferService.selectById(transferId);
        if (head == null) {
            throw new ServiceException("移库单不存在");
        }
        PdaTransferDetailVo vo = new PdaTransferDetailVo();
        vo.setTransferId(head.getTransferId());
        vo.setTransferNo(head.getTransferNo());
        vo.setStatus(head.getStatus());
        vo.setFromWarehouseName(head.getFromWarehouseName());
        vo.setToWarehouseName(head.getToWarehouseName());
        vo.setTransferDate(head.getTransferDate() != null ? DateUtil.formatDate(head.getTransferDate()) : null);

        List<WmTransferLine> lines = lineMapper.selectList(
            Wrappers.<WmTransferLine>lambdaQuery().eq(WmTransferLine::getTransferId, transferId));
        List<PdaTransferDetailVo.PdaTransferLineVo> lineVos = new ArrayList<>(lines.size());
        for (WmTransferLine line : lines) {
            PdaTransferDetailVo.PdaTransferLineVo lv = new PdaTransferDetailVo.PdaTransferLineVo();
            lv.setLineId(line.getLineId());
            lv.setMaterialCode(line.getMaterialCode());
            lv.setMaterialName(line.getMaterialName());
            lv.setMaterialSpec(line.getMaterialSpec());
            lv.setBaseUomName(line.getBaseUomName());
            lv.setBatchCode(line.getBatchCode());
            lv.setFromLocationCode(line.getFromLocationCode());
            lv.setToLocationCode(line.getToLocationCode());
            lv.setQuantity(line.getQuantity());
            lv.setTakeoffQty(line.getTakeoffQty());
            lineVos.add(lv);
        }
        vo.setLines(lineVos);
        return vo;
    }

    @Override
    public PdaActionResultVo takeoff(Long transferId, PdaTransferTakeoffBo bo) {
        WmTransferVo head = transferService.selectById(transferId);
        if (head == null) {
            throw new ServiceException("移库单不存在");
        }
        String status = head.getStatus();
        if (!IWmTransferService.STATUS_APPROVED.equals(status)
            && !IWmTransferService.STATUS_TAKEN_OFF.equals(status)) {
            return PdaActionResultVo.fail("单据当前状态不允许下架");
        }
        WmTransferLine line = lineMapper.selectOne(Wrappers.<WmTransferLine>lambdaQuery()
            .eq(WmTransferLine::getLineId, bo.getLineId())
            .eq(WmTransferLine::getTransferId, transferId));
        if (line == null) {
            return PdaActionResultVo.fail("行不属于本移库单");
        }
        if (line.getTakeoffQty() != null) {
            return PdaActionResultVo.fail("该行已下架");
        }
        if (!StringUtils.equals(bo.getLocationCode(), line.getFromLocationCode())) {
            PdaActionResultVo r = PdaActionResultVo.fail("源库位不符，请扫 " + line.getFromLocationCode());
            r.setRecommendedLocation(line.getFromLocationCode());
            return r;
        }
        BigDecimal qty = bo.getQty() == null ? line.getQuantity() : bo.getQty();
        if (line.getQuantity() != null && qty.compareTo(line.getQuantity()) != 0) {
            return PdaActionResultVo.fail("下架数量须等于行数量(暂不支持部分下架)");
        }
        line.setTakeoffQty(qty);
        lineMapper.updateById(line);

        // 全部行已下架 → 单头转 TAKEN_OFF
        Long remain = lineMapper.selectCount(Wrappers.<WmTransferLine>lambdaQuery()
            .eq(WmTransferLine::getTransferId, transferId)
            .isNull(WmTransferLine::getTakeoffQty));
        boolean allTakenOff = remain != null && remain == 0L;
        if (allTakenOff && IWmTransferService.STATUS_APPROVED.equals(status)) {
            WmTransfer upd = new WmTransfer();
            upd.setTransferId(transferId);
            upd.setStatus(IWmTransferService.STATUS_TAKEN_OFF);
            transferMapper.updateById(upd);
        }
        return PdaActionResultVo.ok(allTakenOff
            ? PdaActionResultVo.NEXT_SCAN_LOCATION
            : PdaActionResultVo.NEXT_SCAN_MATERIAL);
    }

    @Override
    public PdaActionResultVo shelf(Long transferId, PdaShelfBo bo) {
        WmTransferVo head = transferService.selectById(transferId);
        if (head == null) {
            throw new ServiceException("移库单不存在");
        }
        if (IWmTransferService.STATUS_EXECUTED.equals(head.getStatus())) {
            return PdaActionResultVo.ok(PdaActionResultVo.NEXT_FINISHED); // 幂等: 已过账
        }
        if (!IWmTransferService.STATUS_TAKEN_OFF.equals(head.getStatus())) {
            return PdaActionResultVo.fail("请先完成全部行下架");
        }
        List<WmTransferLine> lines = lineMapper.selectList(
            Wrappers.<WmTransferLine>lambdaQuery().eq(WmTransferLine::getTransferId, transferId));
        Set<String> targets = new LinkedHashSet<>();
        for (WmTransferLine l : lines) {
            if (StringUtils.isNotBlank(l.getToLocationCode())) {
                targets.add(l.getToLocationCode());
            }
        }
        if (!targets.isEmpty() && !targets.contains(bo.getLocationCode())) {
            PdaActionResultVo r = PdaActionResultVo.fail("目标库位不符，请扫目标库位");
            r.setRecommendedLocation(targets.iterator().next());
            return r;
        }
        transferService.execute(transferId);
        return PdaActionResultVo.ok(PdaActionResultVo.NEXT_FINISHED);
    }

    @Override
    public PdaActionResultVo execute(Long transferId) {
        WmTransferVo head = transferService.selectById(transferId);
        if (head == null) {
            throw new ServiceException("移库单不存在");
        }
        if (!IWmTransferService.STATUS_APPROVED.equals(head.getStatus())) {
            return PdaActionResultVo.fail("单据当前状态不允许执行(需已审批)");
        }
        transferService.execute(transferId);
        return PdaActionResultVo.ok(PdaActionResultVo.NEXT_FINISHED);
    }
}
