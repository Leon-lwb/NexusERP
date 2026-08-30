package com.nexus.erp.wm.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmTransferLine;
import com.nexus.erp.wm.domain.bo.WmTransferBo;
import com.nexus.erp.wm.domain.vo.WmTransferVo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTransferDetailVo;
import com.nexus.erp.wm.mapper.WmTransferLineMapper;
import com.nexus.erp.wm.service.IPdaTransferService;
import com.nexus.erp.wm.service.IWmTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * PDA 移库作业实现 — 编排既有移库服务
 */
@DS("erp")
@Service
@RequiredArgsConstructor
public class PdaTransferServiceImpl implements IPdaTransferService {

    private final IWmTransferService transferService;
    private final WmTransferLineMapper lineMapper;

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
            lineVos.add(lv);
        }
        vo.setLines(lineVos);
        return vo;
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
