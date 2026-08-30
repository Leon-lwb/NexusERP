package com.nexus.erp.wm.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.bo.WmProductionIssueBo;
import com.nexus.erp.wm.domain.bo.pda.PdaIssueFinishBo;
import com.nexus.erp.wm.domain.bo.pda.PdaIssueShortageBo;
import com.nexus.erp.wm.domain.bo.pda.PdaPickBo;
import com.nexus.erp.wm.domain.vo.WmProductionIssueVo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaIssueDetailVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;
import com.nexus.erp.wm.mapper.WmProductionIssueLineMapper;
import com.nexus.erp.wm.service.IPdaIssueService;
import com.nexus.erp.wm.service.IWmProductionIssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * PDA 备料发料作业实现
 * <p>
 * 行级拣货 = 目标行 currentQty 置本次数量(其余行清零)后调用既有 execute(issueId) 过账,
 * 既有实现支持 CREATED/PARTIAL 多次执行, 天然适配摘果式逐项拣货。
 */
@DS("erp")
@Service
@RequiredArgsConstructor
public class PdaIssueServiceImpl implements IPdaIssueService {

    private static final String STATUS_CREATED = "CREATED";
    private static final String STATUS_PARTIAL = "PARTIAL";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final IWmProductionIssueService issueService;
    private final WmProductionIssueLineMapper lineMapper;

    @Override
    public TableDataInfo<PdaTodoItemVo> todo(String keyword, PageQuery pageQuery) {
        WmProductionIssueBo bo = new WmProductionIssueBo();
        if (StringUtils.isNotBlank(keyword)) {
            bo.setIssueCode(keyword);
        }
        TableDataInfo<WmProductionIssueVo> page = issueService.selectPageList(bo, pageQuery);
        List<PdaTodoItemVo> items = new ArrayList<>(page.getRows().size());
        for (WmProductionIssueVo vo : page.getRows()) {
            PdaTodoItemVo item = new PdaTodoItemVo();
            item.setId(vo.getIssueId());
            item.setCode(vo.getIssueCode());
            item.setStatus(vo.getStatus());
            item.setName(vo.getWoNo());
            item.setWarehouseName(vo.getWarehouseName());
            item.setDate(vo.getIssueDate() != null ? DateUtil.formatDate(vo.getIssueDate()) : null);
            items.add(item);
        }
        return new TableDataInfo<>(items, page.getTotal());
    }

    @Override
    public PdaIssueDetailVo detail(Long issueId) {
        WmProductionIssueVo head = issueService.selectById(issueId);
        if (head == null) {
            throw new ServiceException("发料单不存在");
        }
        PdaIssueDetailVo vo = new PdaIssueDetailVo();
        vo.setIssueId(head.getIssueId());
        vo.setIssueCode(head.getIssueCode());
        vo.setStatus(head.getStatus());
        vo.setWoNo(head.getWoNo());
        vo.setWarehouseName(head.getWarehouseName());
        vo.setIssueDate(head.getIssueDate() != null ? DateUtil.formatDate(head.getIssueDate()) : null);

        List<WmProductionIssueLine> lines = lineMapper.selectList(
            Wrappers.<WmProductionIssueLine>lambdaQuery().eq(WmProductionIssueLine::getIssueId, issueId));
        // 按库位编码排序, 近似拣货动线
        lines.sort(Comparator.comparing(WmProductionIssueLine::getLocationCode,
            Comparator.nullsLast(Comparator.naturalOrder())));
        List<PdaIssueDetailVo.PdaIssueLineVo> lineVos = new ArrayList<>(lines.size());
        for (WmProductionIssueLine line : lines) {
            PdaIssueDetailVo.PdaIssueLineVo lv = new PdaIssueDetailVo.PdaIssueLineVo();
            lv.setLineId(line.getLineId());
            lv.setMaterialId(line.getMaterialId());
            lv.setMaterialCode(line.getMaterialCode());
            lv.setMaterialName(line.getMaterialName());
            lv.setMaterialSpec(line.getMaterialSpec());
            lv.setBaseUomName(line.getBaseUomName());
            lv.setBatchCode(line.getBatchCode());
            lv.setLocationCode(line.getLocationCode());
            lv.setQtyToPick(line.getRequiredQty());
            lv.setQtyPicked(line.getIssuedQty());
            lv.setLineStatus(line.getLineStatus());
            lineVos.add(lv);
        }
        vo.setLines(lineVos);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdaActionResultVo pick(Long issueId, PdaPickBo bo) {
        WmProductionIssueVo head = issueService.selectById(issueId);
        if (head == null) {
            throw new ServiceException("发料单不存在");
        }
        if (!STATUS_CREATED.equals(head.getStatus()) && !STATUS_PARTIAL.equals(head.getStatus())) {
            return PdaActionResultVo.fail("单据当前状态不允许拣货");
        }
        List<WmProductionIssueLine> lines = lineMapper.selectList(
            Wrappers.<WmProductionIssueLine>lambdaQuery().eq(WmProductionIssueLine::getIssueId, issueId));
        WmProductionIssueLine target = lines.stream()
            .filter(l -> l.getLineId().equals(bo.getLineId())).findFirst().orElse(null);
        if (target == null) {
            return PdaActionResultVo.fail("单据行不存在");
        }

        BigDecimal required = target.getRequiredQty() == null ? BigDecimal.ZERO : target.getRequiredQty();
        BigDecimal issued = target.getIssuedQty() == null ? BigDecimal.ZERO : target.getIssuedQty();
        if (bo.getQty().add(issued).compareTo(required) > 0) {
            return PdaActionResultVo.fail("拣货数量超出应领数量");
        }
        if (StringUtils.isNotBlank(target.getLocationCode())
            && !target.getLocationCode().equals(bo.getLocationCode())) {
            return PdaActionResultVo.fail("库位不符，请扫 " + target.getLocationCode());
        }

        // 仅目标行置本次领料数量, 其余行清零后调用既有执行过账
        for (WmProductionIssueLine line : lines) {
            boolean isTarget = line.getLineId().equals(bo.getLineId());
            line.setCurrentQty(isTarget ? bo.getQty() : BigDecimal.ZERO);
            if (isTarget) {
                if (StringUtils.isBlank(line.getLocationCode())) {
                    line.setLocationCode(bo.getLocationCode());
                }
                if (StringUtils.isNotBlank(bo.getBatchCode())) {
                    line.setBatchCode(bo.getBatchCode());
                }
            }
            lineMapper.updateById(line);
        }
        issueService.execute(issueId);
        return PdaActionResultVo.ok(PdaActionResultVo.NEXT_SCAN_MATERIAL);
    }

    @Override
    public PdaActionResultVo finish(Long issueId, PdaIssueFinishBo bo) {
        WmProductionIssueVo head = issueService.selectById(issueId);
        if (head == null) {
            throw new ServiceException("发料单不存在");
        }
        if (STATUS_COMPLETED.equals(head.getStatus())) {
            return PdaActionResultVo.ok(PdaActionResultVo.NEXT_FINISHED);
        }
        List<WmProductionIssueLine> lines = lineMapper.selectList(
            Wrappers.<WmProductionIssueLine>lambdaQuery().eq(WmProductionIssueLine::getIssueId, issueId));
        boolean hasUnfulfilled = false;
        for (WmProductionIssueLine line : lines) {
            BigDecimal required = line.getRequiredQty() == null ? BigDecimal.ZERO : line.getRequiredQty();
            BigDecimal issued = line.getIssuedQty() == null ? BigDecimal.ZERO : line.getIssuedQty();
            if (issued.compareTo(required) < 0) {
                hasUnfulfilled = true;
                break;
            }
        }
        if (hasUnfulfilled) {
            List<PdaIssueShortageBo> shortages = bo == null ? null : bo.getShortageLines();
            if (shortages == null || shortages.isEmpty()) {
                return PdaActionResultVo.fail("存在未领满的行，请继续拣货或声明缺货完成");
            }
            issueService.completeWithShortage(issueId, shortages);
            return PdaActionResultVo.ok(PdaActionResultVo.NEXT_FINISHED);
        }
        issueService.execute(issueId);
        return PdaActionResultVo.ok(PdaActionResultVo.NEXT_FINISHED);
    }
}
