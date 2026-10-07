package com.nexus.erp.wm.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.bo.WmProductionIssueBo;
import com.nexus.erp.wm.domain.bo.pda.PdaIssueFinishBo;
import com.nexus.erp.wm.domain.bo.pda.PdaIssueShortageBo;
import com.nexus.erp.wm.domain.bo.pda.PdaPickBo;
import com.nexus.erp.wm.domain.vo.WmProductionIssueVo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaIssueDetailVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;
import com.nexus.erp.wm.mapper.PdaIssueSourceMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueLineMapper;
import com.nexus.erp.wm.service.IPdaIssueService;
import com.nexus.erp.wm.service.IWmProductionIssueService;
import com.nexus.system.service.ISysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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

    /** 发料批次质检门禁开关(sys_config; true=拣货批次必须 IQC 合格才可发料, 缺省关闭) */
    private static final String QC_GATE_CONFIG_KEY = "wm.issue.batch.qcGate";
    /** 批次质检白名单值: 仅该值允许发料 */
    private static final String QC_QUALIFIED = "QUALIFIED";

    private final IWmProductionIssueService issueService;
    private final WmProductionIssueLineMapper lineMapper;
    private final ISysConfigService configService;
    private final PdaIssueSourceMapper sourceMapper;
    private final PdaIssueSourceGuard sourceGuard;

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
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.REPEATABLE_READ)
    public PdaActionResultVo pick(Long issueId, PdaPickBo bo) {
        if (issueId == null || issueId <= 0 || bo == null || bo.getLineId() == null || bo.getLineId() <= 0
            || bo.getQty() == null || bo.getQty().signum() <= 0) {
            return PdaActionResultVo.fail("单据、行标识无效或拣货数量必须大于零");
        }
        String tenantId = TenantHelper.getTenantId();
        if (StringUtils.isBlank(tenantId)) {
            return PdaActionResultVo.fail("当前租户未知，禁止领料");
        }
        // 当前头锁在任何数量、来源查询之前取得，并一直持有到原事务完成。
        WmProductionIssue head = sourceMapper.lockHead(tenantId, issueId);
        if (head == null) {
            throw new ServiceException("发料单不存在");
        }
        if (!tenantId.equals(head.getTenantId()) || !Objects.equals(issueId, head.getIssueId())
            || !"0".equals(head.getDelFlag())) {
            return PdaActionResultVo.fail("发料单租户或有效性不符");
        }
        if (!STATUS_CREATED.equals(head.getStatus()) && !STATUS_PARTIAL.equals(head.getStatus())) {
            return PdaActionResultVo.fail("单据当前状态不允许拣货");
        }
        List<WmProductionIssueLine> lines = sourceMapper.lockLines(tenantId, issueId);
        if (lines == null || lines.isEmpty()) {
            return PdaActionResultVo.fail("单据行不存在");
        }
        WmProductionIssueLine target = null;
        Set<Long> lineIds = new HashSet<>();
        for (WmProductionIssueLine line : lines) {
            if (line == null || line.getLineId() == null || line.getLineId() <= 0 || !lineIds.add(line.getLineId())
                || !tenantId.equals(line.getTenantId()) || !Objects.equals(issueId, line.getIssueId())
                || !"0".equals(line.getDelFlag())) {
                return PdaActionResultVo.fail("领料行租户、单据归属或有效性不符");
            }
            if (line.getLineId().equals(bo.getLineId())) {
                target = line;
            }
        }
        if (target == null) {
            return PdaActionResultVo.fail("单据行不存在");
        }
        if (target.getRequiredQty() == null || target.getIssuedQty() == null) {
            return PdaActionResultVo.fail("应领或已领数量未知，禁止拣货");
        }
        BigDecimal required = target.getRequiredQty();
        BigDecimal issued = target.getIssuedQty();
        if (issued.signum() < 0 || bo.getQty().add(issued).compareTo(required) > 0
            || (target.getShortQty() != null && bo.getQty().compareTo(target.getShortQty()) > 0)
            || "SHORT_CLOSED".equals(target.getLineStatus()) || "ISSUED".equals(target.getLineStatus())) {
            return PdaActionResultVo.fail("拣货数量超出应领/欠领数量或行已关闭");
        }

        PdaIssueSourceGuard.Source source;
        try {
            source = sourceGuard.resolve(tenantId, head, target, bo);
        } catch (PdaIssueSourceGuard.SourceRejectedException rejected) {
            // 只有未写入阶段的业务拒绝可返回失败；写行和实际扣库异常必须向外传播以回滚。
            return PdaActionResultVo.fail(rejected.getMessage());
        }
        if (source.batch() != null
            && "true".equalsIgnoreCase(StringUtils.trimToEmpty(configService.selectConfigByKey(QC_GATE_CONFIG_KEY)))
            && !QC_QUALIFIED.equals(source.batch().getQcStatus())) {
            String qc = StringUtils.isBlank(source.batch().getQcStatus()) ? "待检/未回写" : source.batch().getQcStatus();
            return PdaActionResultVo.fail("批次 " + source.batch().getBatchCode() + " IQC质检状态为 " + qc + "，禁止发料");
        }

        // 仅在完整验证通过后绑定真实标识和编码，清零其余锁内行，再沿原事务调用过账。
        for (WmProductionIssueLine line : lines) {
            boolean isTarget = line.getLineId().equals(bo.getLineId());
            line.setCurrentQty(isTarget ? bo.getQty() : BigDecimal.ZERO);
            if (isTarget) {
                source.bindTo(line);
            }
            if (lineMapper.updateById(line) != 1) {
                throw new ServiceException("领料行写入失败，请刷新后重试");
            }
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
