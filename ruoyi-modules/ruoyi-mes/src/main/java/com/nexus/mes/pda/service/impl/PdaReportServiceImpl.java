package com.nexus.mes.pda.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pda.domain.bo.PdaReportCompleteBo;
import com.nexus.mes.pda.domain.vo.MesActionResultVo;
import com.nexus.mes.pda.domain.vo.PdaDispatchItemVo;
import com.nexus.mes.pda.service.IPdaReportService;
import com.nexus.mes.pro.domain.ProDispatch;
import com.nexus.mes.pro.mapper.ProDispatchMapper;
import com.nexus.mes.pro.service.IProDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * PDA 扫码报工实现 — 编排既有派工服务
 * <p>
 * 派工单即报工载体: 开工 startDispatch(PENDING→IN_PROGRESS),
 * 完工 completeDispatch(数量校验+CAS+回写工序/工单)。
 * <p>
 * 事务边界(MES-PDA-05): 本类 <b>不再</b> 标注 {@code @Transactional} —— 原实现在外层事务内
 * catch 内层 {@code @Transactional} 方法抛出的异常并正常返回, 共享事务已被标记 rollback-only,
 * 提交期抛 {@code UnexpectedRollbackException}(客户端拿到不可读的 500)。
 * 现在: 事务由 {@link IProDispatchService} 各方法自行持有, 可预期失败改为 <b>业务校验前置</b>,
 * 内层异常在事务外捕获后以可读 {@link MesActionResultVo#fail(String)} 返回。
 */
@Slf4j
@DS("mes")
@Service
@RequiredArgsConstructor
public class PdaReportServiceImpl implements IPdaReportService {

    private final IProDispatchService dispatchService;
    private final ProDispatchMapper dispatchMapper;

    @Override
    public TableDataInfo<PdaDispatchItemVo> todo(String keyword, PageQuery pageQuery) {
        Page<ProDispatch> page = dispatchMapper.selectPage(pageQuery.build(),
            Wrappers.<ProDispatch>lambdaQuery()
                .in(ProDispatch::getDispatchStatus, Arrays.asList(
                    IProDispatchService.STATUS_PENDING, IProDispatchService.STATUS_IN_PROGRESS))
                .and(StringUtils.isNotBlank(keyword), w -> w
                    .like(ProDispatch::getWoNo, keyword)
                    .or().like(ProDispatch::getDispatchNo, keyword))
                .orderByAsc(ProDispatch::getDispatchStatus)
                .orderByDesc(ProDispatch::getCreateTime));
        List<PdaDispatchItemVo> items = new ArrayList<>(page.getRecords().size());
        for (ProDispatch d : page.getRecords()) {
            items.add(toVo(d));
        }
        return new TableDataInfo<>(items, page.getTotal());
    }

    @Override
    public PdaDispatchItemVo detail(Long dispatchId) {
        ProDispatch d = dispatchMapper.selectById(dispatchId);
        if (d == null) {
            throw new ServiceException("派工单不存在");
        }
        return toVo(d);
    }

    @Override
    public MesActionResultVo start(Long dispatchId) {
        ProDispatch d = dispatchMapper.selectById(dispatchId);
        if (d == null) {
            throw new ServiceException("派工单不存在");
        }
        if (IProDispatchService.STATUS_IN_PROGRESS.equals(d.getDispatchStatus())) {
            return MesActionResultVo.ok("已在进行中"); // 幂等
        }
        if (!IProDispatchService.STATUS_PENDING.equals(d.getDispatchStatus())) {
            return MesActionResultVo.fail("当前状态不允许开工: " + d.getDispatchStatus());
        }
        try {
            dispatchService.startDispatch(dispatchId);
        } catch (RuntimeException e) {
            log.warn("[PDA报工] 开工失败: {}", e.getMessage());
            return MesActionResultVo.fail(readable(e));
        }
        return MesActionResultVo.ok("已开工");
    }

    @Override
    public MesActionResultVo complete(Long dispatchId, PdaReportCompleteBo bo) {
        ProDispatch d = dispatchMapper.selectById(dispatchId);
        if (d == null) {
            throw new ServiceException("派工单不存在");
        }
        if (IProDispatchService.STATUS_COMPLETED.equals(d.getDispatchStatus())) {
            return MesActionResultVo.ok("已完成"); // 幂等
        }
        if (!IProDispatchService.STATUS_IN_PROGRESS.equals(d.getDispatchStatus())) {
            return MesActionResultVo.fail("请先开工后再报工");
        }

        BigDecimal goodQty = bo.getGoodQty();
        BigDecimal defectQty = bo.getDefectQty() == null ? BigDecimal.ZERO : bo.getDefectQty();
        BigDecimal scrapQty = bo.getScrapQty() == null ? BigDecimal.ZERO : bo.getScrapQty();
        // 业务校验前置: 可预期失败不依赖内层事务回滚, 直接返回可读原因
        String invalid = validateQuantities(goodQty, defectQty, scrapQty, d.getPlannedQty());
        if (invalid != null) {
            return MesActionResultVo.fail(invalid);
        }

        try {
            dispatchService.completeDispatch(dispatchId, goodQty, defectQty, scrapQty);
        } catch (RuntimeException e) {
            log.warn("[PDA报工] 完工失败: {}", e.getMessage());
            return MesActionResultVo.fail(readable(e));
        }
        return MesActionResultVo.ok("报工完成");
    }

    /**
     * 报工数量前置校验(含超报上限)
     * <p>
     * 超报上限: 合计不得超过派工计划数量(修复前无上界守卫, 一旦放开"必须相等"即会超报)。
     * 少报(合计 &lt; 计划数)仍由派工服务的一次性完工模型拒绝并回传可读原因 ——
     * 分批报工需要 {@code pro_dispatch} 增加"已报数量"累计语义与原子守卫(见修复报告未修复项)。
     * </p>
     *
     * @return 校验不通过时的可读原因, 通过时返回 {@code null}
     */
    private String validateQuantities(BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty,
                                      BigDecimal plannedQty) {
        if (goodQty == null) {
            return "合格数量不能为空";
        }
        if (goodQty.compareTo(BigDecimal.ZERO) < 0
            || defectQty.compareTo(BigDecimal.ZERO) < 0
            || scrapQty.compareTo(BigDecimal.ZERO) < 0) {
            return "报工数量不能为负数";
        }
        BigDecimal total = goodQty.add(defectQty).add(scrapQty);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return "报工数量合计必须大于0";
        }
        if (plannedQty != null && total.compareTo(plannedQty) > 0) {
            return String.format("报工数量合计不能超过派工计划数量 %s(当前合计 %s)",
                plannedQty.stripTrailingZeros().toPlainString(), total.stripTrailingZeros().toPlainString());
        }
        return null;
    }

    /**
     * 提取可读失败原因(兜底避免把 null/空消息返回给 PDA)
     */
    private String readable(Throwable e) {
        String msg = e.getMessage();
        return StringUtils.isNotBlank(msg) ? msg : "报工处理失败，请稍后重试";
    }

    private PdaDispatchItemVo toVo(ProDispatch d) {
        PdaDispatchItemVo vo = new PdaDispatchItemVo();
        vo.setDispatchId(d.getDispatchId());
        vo.setDispatchNo(d.getDispatchNo());
        vo.setWoNo(d.getWoNo());
        vo.setProcessCode(d.getProcessCode());
        vo.setProcessName(d.getProcessName());
        vo.setWorkcenterCode(d.getWorkcenterCode());
        vo.setOperatorName(d.getOperatorName());
        vo.setPlannedQty(d.getPlannedQty());
        vo.setCompletedQty(d.getCompletedQty());
        vo.setDispatchStatus(d.getDispatchStatus());
        return vo;
    }
}
