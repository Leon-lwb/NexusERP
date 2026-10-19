package com.nexus.erp.wm.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmMaterialReturn;
import com.nexus.erp.wm.domain.WmMaterialReturnLine;
import com.nexus.erp.wm.domain.WmMiscRecpt;
import com.nexus.erp.wm.domain.WmMiscRecptLine;
import com.nexus.erp.wm.domain.bo.pda.PdaReturnConfirmBo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaReturnDetailVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;
import com.nexus.erp.wm.mapper.WmMaterialReturnLineMapper;
import com.nexus.erp.wm.mapper.WmMaterialReturnMapper;
import com.nexus.erp.wm.service.IPdaReturnService;
import com.nexus.erp.wm.service.IWmStockOperationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * PDA 退料作业实现
 * <p>
 * 落账策略: 良品/不良品按实退数量经杂项入库通道回库存(库位取建行指定的目标库位,
 * 不良品建议指向不良库区); 报废仅留痕不回库存。流水来源记录为退料单ID。
 */
@DS("erp")
@Service
@RequiredArgsConstructor
public class PdaReturnServiceImpl implements IPdaReturnService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.nexus.erp.wm.service.WmSerialInventoryService serialInventory;

    private final WmMaterialReturnMapper returnMapper;
    private final WmMaterialReturnLineMapper lineMapper;
    private final IWmStockOperationService stockOperationService;

    @Override
    public TableDataInfo<PdaTodoItemVo> todo(String keyword, PageQuery pageQuery) {
        Page<WmMaterialReturn> page =
                returnMapper.selectPage(
                        pageQuery.build(),
                        Wrappers.<WmMaterialReturn>lambdaQuery()
                                .like(
                                        StringUtils.isNotBlank(keyword),
                                        WmMaterialReturn::getReturnNo,
                                        keyword)
                                .orderByDesc(WmMaterialReturn::getCreateTime));
        List<PdaTodoItemVo> items = new ArrayList<>(page.getRecords().size());
        for (WmMaterialReturn r : page.getRecords()) {
            PdaTodoItemVo item = new PdaTodoItemVo();
            item.setId(r.getReturnId());
            item.setCode(r.getReturnNo());
            item.setStatus(r.getStatus());
            item.setName(
                    typeLabel(r.getReturnType())
                            + (StringUtils.isNotBlank(r.getSourceDoc())
                                    ? " · " + r.getSourceDoc()
                                    : ""));
            item.setWarehouseName(r.getWarehouseName());
            item.setDate(r.getReturnDate() != null ? DateUtil.formatDate(r.getReturnDate()) : null);
            items.add(item);
        }
        return new TableDataInfo<>(items, page.getTotal());
    }

    @Override
    public PdaReturnDetailVo detail(Long returnId) {
        WmMaterialReturn head = headOrThrow(returnId);
        PdaReturnDetailVo vo = new PdaReturnDetailVo();
        vo.setReturnId(head.getReturnId());
        vo.setReturnNo(head.getReturnNo());
        vo.setReturnType(head.getReturnType());
        vo.setStatus(head.getStatus());
        vo.setWarehouseName(head.getWarehouseName());
        vo.setSourceDoc(head.getSourceDoc());
        vo.setReturnDate(
                head.getReturnDate() != null ? DateUtil.formatDate(head.getReturnDate()) : null);

        List<WmMaterialReturnLine> lines = linesOf(returnId);
        List<PdaReturnDetailVo.PdaReturnLineVo> lineVos = new ArrayList<>(lines.size());
        for (WmMaterialReturnLine line : lines) {
            PdaReturnDetailVo.PdaReturnLineVo lv = new PdaReturnDetailVo.PdaReturnLineVo();
            lv.setLineId(line.getLineId());
            lv.setMaterialCode(line.getMaterialCode());
            lv.setMaterialName(line.getMaterialName());
            lv.setMaterialSpec(line.getMaterialSpec());
            lv.setBaseUomName(line.getBaseUomName());
            lv.setBatchCode(line.getBatchCode());
            lv.setLocationCode(line.getLocationCode());
            lv.setPlanQty(line.getQuantity());
            lv.setActualQty(line.getActualQty() == null ? BigDecimal.ZERO : line.getActualQty());
            lineVos.add(lv);
        }
        vo.setLines(lineVos);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdaActionResultVo confirmLine(Long returnId, PdaReturnConfirmBo bo) {
        WmMaterialReturn head = headOrThrow(returnId);
        if (WmMaterialReturn.STATUS_COMPLETED.equals(head.getStatus())) {
            return PdaActionResultVo.fail("退料单已完成");
        }
        WmMaterialReturnLine line =
                lineMapper.selectOne(
                        Wrappers.<WmMaterialReturnLine>lambdaQuery()
                                .eq(WmMaterialReturnLine::getLineId, bo.getLineId())
                                .eq(WmMaterialReturnLine::getReturnId, returnId));
        if (line == null) {
            return PdaActionResultVo.fail("行不属于本退料单");
        }
        BigDecimal actual = line.getActualQty() == null ? BigDecimal.ZERO : line.getActualQty();
        BigDecimal plan = line.getQuantity() == null ? BigDecimal.ZERO : line.getQuantity();
        if (plan.compareTo(BigDecimal.ZERO) > 0 && actual.add(bo.getQty()).compareTo(plan) > 0) {
            return PdaActionResultVo.fail("累计实退超过计划数量(" + plan + ")");
        }
        line.setActualQty(actual.add(bo.getQty()));
        if (StringUtils.isNotBlank(bo.getBatchCode())) {
            line.setBatchCode(bo.getBatchCode());
        }
        lineMapper.updateById(line);
        return PdaActionResultVo.ok(PdaActionResultVo.NEXT_SCAN_MATERIAL);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdaActionResultVo finish(Long returnId) {
        WmMaterialReturn head = headOrThrow(returnId);
        if (WmMaterialReturn.STATUS_COMPLETED.equals(head.getStatus())) {
            return PdaActionResultVo.ok(PdaActionResultVo.NEXT_FINISHED); // 幂等
        }
        if (!WmMaterialReturn.STATUS_DRAFT.equals(head.getStatus())) {
            return PdaActionResultVo.fail("单据当前状态不允许完成");
        }
        List<WmMaterialReturnLine> lines = linesOf(returnId);
        boolean anyActual =
                lines.stream()
                        .anyMatch(
                                l ->
                                        l.getActualQty() != null
                                                && l.getActualQty().compareTo(BigDecimal.ZERO) > 0);
        if (!anyActual) {
            return PdaActionResultVo.fail("无实退记录，请先扫码核销");
        }

        Date now = new Date();
        // 杂项入库通道包装: 单据引用指向退料单
        WmMiscRecpt wrapper = new WmMiscRecpt();
        wrapper.setRecptId(head.getReturnId());
        wrapper.setWarehouseId(head.getWarehouseId());
        wrapper.setWarehouseCode(head.getWarehouseCode());
        wrapper.setWarehouseName(head.getWarehouseName());

        for (WmMaterialReturnLine line : lines) {
            if (line.getActualQty() == null
                    || line.getActualQty().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (serialInventory != null
                    && serialInventory.postSource(
                            "WM_MATERIAL_RETURN", returnId, line.getLineId())) {
                continue;
            }
            if (WmMaterialReturn.TYPE_SCRAP.equals(head.getReturnType())) {
                continue; // 报废: 留痕不回库存
            }
            WmMiscRecptLine post = new WmMiscRecptLine();
            post.setRecptId(head.getReturnId());
            post.setLineId(line.getLineId());
            post.setMaterialId(line.getMaterialId());
            post.setMaterialCode(line.getMaterialCode());
            post.setMaterialName(line.getMaterialName());
            post.setMaterialSpec(line.getMaterialSpec());
            post.setBaseUomId(line.getBaseUomId());
            post.setBaseUomName(line.getBaseUomName());
            post.setBatchId(line.getBatchId());
            post.setBatchCode(line.getBatchCode());
            post.setLocationId(line.getLocationId());
            post.setLocationCode(line.getLocationCode());
            post.setLocationName(line.getLocationName());
            post.setQuantity(line.getActualQty());
            stockOperationService.postMiscRecpt(post, wrapper, now);
        }

        head.setStatus(WmMaterialReturn.STATUS_COMPLETED);
        head.setReturnDate(now);
        returnMapper.updateById(head);
        return PdaActionResultVo.ok(PdaActionResultVo.NEXT_FINISHED);
    }

    private WmMaterialReturn headOrThrow(Long returnId) {
        WmMaterialReturn head = returnMapper.selectById(returnId);
        if (head == null) {
            throw new ServiceException("退料单不存在");
        }
        return head;
    }

    private List<WmMaterialReturnLine> linesOf(Long returnId) {
        return lineMapper.selectList(
                Wrappers.<WmMaterialReturnLine>lambdaQuery()
                        .eq(WmMaterialReturnLine::getReturnId, returnId));
    }

    private String typeLabel(String type) {
        if (WmMaterialReturn.TYPE_GOOD.equals(type)) {
            return "良品退料";
        }
        if (WmMaterialReturn.TYPE_DEFECT.equals(type)) {
            return "不良退料";
        }
        if (WmMaterialReturn.TYPE_SCRAP.equals(type)) {
            return "报废退料";
        }
        return "退料";
    }
}
