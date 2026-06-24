package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.bo.WmProductionIssueBo;
import com.nexus.erp.wm.domain.vo.WmMaterialStockVo;
import com.nexus.erp.wm.domain.vo.WmProductionIssueVo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueLineMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueMapper;
import com.nexus.erp.wm.service.IWmProductionIssueService;
import com.nexus.erp.wm.service.IWmStockOperationService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.rmi.ServerException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmProductionIssueServiceImpl implements IWmProductionIssueService {

    /** 领料单/行状态常量 */
    private static final String STATUS_CREATED = "CREATED";
    private static final String STATUS_PARTIAL = "PARTIAL";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_EXECUTED = "EXECUTED";   // 历史值，等价于已完成
    private static final String LINE_PENDING = "PENDING";
    private static final String LINE_PARTIAL = "PARTIAL";
    private static final String LINE_ISSUED = "ISSUED";

    private final WmProductionIssueMapper baseMapper;
    private final WmProductionIssueLineMapper lineMapper;
    private final WmMaterialStockMapper stockMapper;
    /** 解耦：注入库存操作接口而非实现 */
    private final IWmStockOperationService stockOperationService;
    private final AutoCodeUtil autoCodeUtil;
    private final PrdWorkOrderMapper workOrderMapper;
    private final PrdMaterialRequirementMapper requirementMapper;
    private final PrdMaterialRequirementDetailMapper detailMapper;

    @Override
    public TableDataInfo<WmProductionIssueVo> selectPageList(WmProductionIssueBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmProductionIssue> lqw = buildQueryWrapper(bo);
        Page<WmProductionIssueVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmProductionIssueVo> selectList(WmProductionIssueBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmProductionIssueVo selectById(Long id) {
        WmProductionIssueVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getIssueId() != null) {
            LambdaQueryWrapper<WmProductionIssueLine> lineLqw = Wrappers.lambdaQuery();
            lineLqw.eq(WmProductionIssueLine::getIssueId, vo.getIssueId());
            vo.setWmProductionIssueLineList(lineMapper.selectList(lineLqw));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmProductionIssueBo bo) {
        WmProductionIssue entity = MapstructUtils.convert(bo, WmProductionIssue.class);
        if (StringUtils.isBlank(entity.getIssueCode())) {
            entity.setIssueCode(autoCodeUtil.genSerialCode("WM_PROD_ISSUE_CODE", null));
        }
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus(STATUS_CREATED);
        }
        if (StringUtils.isBlank(entity.getIssueType())) {
            entity.setIssueType("NORMAL");
        }
        // 未指定领料人时默认取当前操作人
        if (entity.getIssuerId() == null) {
            entity.setIssuerId(entity.getCreateBy());
        }
        int rows = baseMapper.insert(entity);
        insertOrUpdateLines(entity.getIssueId(), bo.getWmProductionIssueLineList());
        recalcHeaderTotals(entity.getIssueId());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmProductionIssueBo bo) {
        WmProductionIssue entity = MapstructUtils.convert(bo, WmProductionIssue.class);
        // 增量同步行：保留 line_id，避免全删全插导致库存流水 source_doc_line_id 失联
        syncLines(entity.getIssueId(), bo.getWmProductionIssueLineList());
        recalcHeaderTotals(entity.getIssueId());
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteLinesByIssueId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 执行生产领料过账 — 支持多次部分领料。
     * <p>
     * 逐行按本次领料数量({@code current_qty})扣减库存（复用 {@link IWmStockOperationService#postProductionIssue}
     * 的 FEFO 跨批次扣减与流水记录），累计已领、重算欠领，current_qty 清零等待下次。
     * 扣减前对每行做可用量(ATP)校验，防止超扣；整个过程置于单事务，任一行失败则整体回滚。
     * <p>
     * 并发说明：同一领料单不建议并发执行；库存扣减走 {@code WmMaterialStock} 的 {@code @Version}
     * 乐观锁，若多张领料单同时扣减同一批次库存可能抛 {@code OptimisticLockingFailureException}
     * 导致本单回滚，调用方可重试。事务内重试无效（失败已标记回滚），故未在此内置重试。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long issueId) {
        WmProductionIssue issue = baseMapper.selectById(issueId);
        if (issue == null) {
            throw new RuntimeException("生产领料单不存在");
        }
        String status = issue.getStatus();
        if (STATUS_COMPLETED.equals(status) || STATUS_EXECUTED.equals(status)) {
            throw new RuntimeException("生产领料单已完成，不可再次领料");
        }
        if (!STATUS_CREATED.equals(status) && !STATUS_PARTIAL.equals(status)) {
            throw new RuntimeException("当前状态不允许执行领料：" + status);
        }

        List<WmProductionIssueLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(WmProductionIssueLine.class)
                .eq(WmProductionIssueLine::getIssueId, issueId));
        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("生产领料单无明细行");
        }

        Date now = new Date();
        boolean hasPick = false;
        boolean allIssued = true;

        for (WmProductionIssueLine line : lines) {
            BigDecimal currentQty = line.getCurrentQty();
            // 本次不领料的行：仅判断是否已领满以决定单头状态
            if (currentQty == null || currentQty.compareTo(BigDecimal.ZERO) <= 0) {
                if (!isLineFullyIssued(line)) {
                    allIssued = false;
                }
                continue;
            }
            hasPick = true;

            BigDecimal requiredQty = zeroIfNull(line.getRequiredQty());
            BigDecimal issuedQty = zeroIfNull(line.getIssuedQty());
            // 欠领 = 应领 - 已领；应领为空（手工行）时不设上限，以可用量为准
            BigDecimal shortQty = requiredQty.compareTo(BigDecimal.ZERO) > 0
                ? requiredQty.subtract(issuedQty) : null;
            if (line.getShortQty() != null) {
                shortQty = line.getShortQty();
            }

            // 校验不超过欠领数量（有应领定额的行）
            if (shortQty != null && shortQty.compareTo(BigDecimal.ZERO) > 0
                && currentQty.compareTo(shortQty) > 0) {
                throw new RuntimeException("物料【" + line.getMaterialName() + "】本次领料数量(" + currentQty
                    + ")超过欠领数量(" + shortQty + ")");
            }

            // 可用量(ATP)校验：按行指定的批次/库位（为空则不限）汇总。
            // 不按头部仓库过滤——物料可能存放在其他仓库，行的批次/库位已唯一指向具体库存，
            // 与 ProductionIssueStrategy 的扣减范围保持一致。
            BigDecimal available = sumAvailable(line.getMaterialId(), null,
                line.getBatchId(), line.getLocationId());
            if (available.compareTo(currentQty) < 0) {
                throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不足，可用量：" + available);
            }

            // 策略读取 line.getQuantity() 扣减；为不污染 quantity 的应领语义，
            // 备份原值，临时令 quantity=本次领料量，扣减后恢复。
            BigDecimal originQuantity = line.getQuantity();
            line.setQuantity(currentQty);
            stockOperationService.postProductionIssue(line, now);
            line.setQuantity(originQuantity);

            // 累计已领、重算欠领、本次清零
            BigDecimal newIssued = issuedQty.add(currentQty);
            BigDecimal newShort = requiredQty.compareTo(BigDecimal.ZERO) > 0
                ? requiredQty.subtract(newIssued) : BigDecimal.ZERO;
            if (newShort.compareTo(BigDecimal.ZERO) < 0) {
                newShort = BigDecimal.ZERO;
            }
            line.setIssuedQty(newIssued);
            line.setShortQty(newShort);
            line.setCurrentQty(null);
            if (requiredQty.compareTo(BigDecimal.ZERO) > 0 && newShort.compareTo(BigDecimal.ZERO) <= 0) {
                line.setLineStatus(LINE_ISSUED);
            } else {
                line.setLineStatus(LINE_PARTIAL);
                allIssued = false;
            }
            lineMapper.updateById(line);

            // 回写工单物料需求明细的已发量/欠料量
            updateRequirementDetail(line.getRequirementDetailId(), currentQty);
        }

        if (!hasPick) {
            throw new RuntimeException("没有需要领料的行（本次领料数量均为0），请填写本次领料数量后再执行");
        }

        issue.setStatus(allIssued ? STATUS_COMPLETED : STATUS_PARTIAL);
        recalcHeaderTotals(issueId);
        baseMapper.updateById(issue);
        log.info("生产领料单执行完成, issueId={}, status={}", issueId, issue.getStatus());
    }

    @Override
    public List<WmMaterialStockVo> selectAvailableStock(Long materialId, Long warehouseId) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, materialId);
        lqw.eq(warehouseId != null, WmMaterialStock::getWarehouseId, warehouseId);
        // 仅查未冻结库存：frozen_flag 字典 sys_true_or_false，0=否(未冻结/可用)，1=是(已冻结)
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        lqw.gt(WmMaterialStock::getStockQuantity, BigDecimal.ZERO);
        lqw.orderByAsc(WmMaterialStock::getExpireDate, WmMaterialStock::getRecptDate, WmMaterialStock::getStockId);
        return stockMapper.selectVoList(lqw);
    }

    /**
     * 批量查询多个物料的总可用库存（汇总所有仓库/批次/库位），
     * 供领料明细列表展示每行物料的"可用库存"参考列。
     */
    @Override
    public Map<Long, BigDecimal> sumAvailableStockByMaterials(List<Long> materialIds) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (materialIds == null || materialIds.isEmpty()) {
            return result;
        }
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.in(WmMaterialStock::getMaterialId, materialIds);
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        lqw.gt(WmMaterialStock::getStockQuantity, BigDecimal.ZERO);
        List<WmMaterialStock> list = stockMapper.selectList(lqw);
        for (WmMaterialStock s : list) {
            result.merge(s.getMaterialId(), zeroIfNull(s.getStockQuantity()), BigDecimal::add);
        }
        return result;
    }

    @Override
    public List<WmProductionIssueLine> buildLinesFromWorkOrder(Long woId) {
        PrdWorkOrder wo = workOrderMapper.selectById(woId);
        if (wo == null || wo.getPlanId() == null) {
            return Collections.emptyList();
        }
        List<PrdMaterialRequirement> reqs = requirementMapper.selectList(
            Wrappers.lambdaQuery(PrdMaterialRequirement.class)
                .eq(PrdMaterialRequirement::getPlanId, wo.getPlanId()));
        if (reqs.isEmpty()) {
            return Collections.emptyList();
        }
        List<WmProductionIssueLine> lines = new ArrayList<>();
        for (PrdMaterialRequirement req : reqs) {
            List<PrdMaterialRequirementDetail> details = detailMapper.selectList(
                Wrappers.lambdaQuery(PrdMaterialRequirementDetail.class)
                    .eq(PrdMaterialRequirementDetail::getRequirementId, req.getRequirementId()));
            for (PrdMaterialRequirementDetail detail : details) {
                // 倒冲物料不进领料单（完工时自动倒冲）
                if (detail.getBackflushFlag() != null && detail.getBackflushFlag() == 1) {
                    continue;
                }
                BigDecimal required = zeroIfNull(detail.getRequiredQuantity());
                WmProductionIssueLine line = new WmProductionIssueLine();
                line.setMaterialId(detail.getMaterialId());
                line.setMaterialCode(detail.getMaterialCode());
                line.setMaterialName(detail.getMaterialName());
                line.setMaterialSpec(detail.getMaterialSpec());
                line.setBaseUomId(detail.getUnit());
                line.setBomQty(detail.getBomQuantity());
                line.setLossRate(detail.getLossRate());
                line.setRequiredQty(required);
                line.setIssuedQty(BigDecimal.ZERO);
                line.setCurrentQty(required);
                line.setShortQty(required);
                line.setRequirementDetailId(detail.getDetailId());
                line.setLineStatus(LINE_PENDING);
                lines.add(line);
            }
        }
        return lines;
    }

    // ===================== 私有辅助 =====================

    private void insertOrUpdateLines(Long issueId, List<WmProductionIssueLine> lineList) {
        if (lineList != null && !lineList.isEmpty()) {
            for (WmProductionIssueLine line : lineList) {
                line.setLineId(null);
                line.setIssueId(issueId);
                // 兜底初始化新列，避免旧数据/手工新增行出现 null
                if (line.getIssuedQty() == null) {
                    line.setIssuedQty(BigDecimal.ZERO);
                }
                if (StringUtils.isBlank(line.getLineStatus())) {
                    line.setLineStatus(LINE_PENDING);
                }
                if (line.getRequiredQty() != null && line.getShortQty() == null) {
                    line.setShortQty(line.getRequiredQty().subtract(line.getIssuedQty()));
                }
                lineMapper.insert(line);
            }
        }
    }

    /**
     * 增量同步领料单行（保留 line_id，保证库存流水 source_doc_line_id 可追溯）：
     * <ul>
     *   <li>前端传来且有 lineId 的行 → 更新可编辑字段，<b>保留后端维护的</b>
     *       issued_qty/short_qty/line_status/required_qty/bom_qty/loss_rate/requirement_detail_id</li>
     *   <li>前端传来但无 lineId 的行 → 新增</li>
     *   <li>后端已有但前端未传的行 → 删除（仅当未领料 issued_qty<=0 时才删，避免误删已领料行）</li>
     * </ul>
     */
    private void syncLines(Long issueId, List<WmProductionIssueLine> lineList) {
        if (issueId == null) {
            return;
        }
        // 后端现有行
        List<WmProductionIssueLine> existLines = lineMapper.selectList(
            Wrappers.lambdaQuery(WmProductionIssueLine.class)
                .eq(WmProductionIssueLine::getIssueId, issueId));
        Map<Long, WmProductionIssueLine> existMap = new HashMap<>();
        for (WmProductionIssueLine l : existLines) {
            existMap.put(l.getLineId(), l);
        }

        Set<Long> keepIds = new HashSet<>();
        if (lineList != null) {
            for (WmProductionIssueLine line : lineList) {
                if (line.getLineId() != null && existMap.containsKey(line.getLineId())) {
                    // 更新：以后端现值为准保留领料执行结果字段，以前端为准更新可编辑字段
                    WmProductionIssueLine db = existMap.get(line.getLineId());
                    line.setIssueId(issueId);
                    line.setIssuedQty(db.getIssuedQty());
                    line.setShortQty(db.getShortQty());
                    line.setLineStatus(db.getLineStatus());
                    line.setRequiredQty(line.getRequiredQty() != null ? line.getRequiredQty() : db.getRequiredQty());
                    line.setBomQty(line.getBomQty() != null ? line.getBomQty() : db.getBomQty());
                    line.setLossRate(line.getLossRate() != null ? line.getLossRate() : db.getLossRate());
                    line.setRequirementDetailId(line.getRequirementDetailId() != null
                        ? line.getRequirementDetailId() : db.getRequirementDetailId());
                    lineMapper.updateById(line);
                    keepIds.add(line.getLineId());
                } else {
                    // 新增行
                    line.setLineId(null);
                    line.setIssueId(issueId);
                    if (line.getIssuedQty() == null) {
                        line.setIssuedQty(BigDecimal.ZERO);
                    }
                    if (StringUtils.isBlank(line.getLineStatus())) {
                        line.setLineStatus(LINE_PENDING);
                    }
                    if (line.getRequiredQty() != null && line.getShortQty() == null) {
                        line.setShortQty(line.getRequiredQty().subtract(line.getIssuedQty()));
                    }
                    lineMapper.insert(line);
                }
            }
        }
        // 删除前端未保留且未领料的旧行（已领料的行不允许删，防止流水孤立）
        for (WmProductionIssueLine db : existLines) {
            if (!keepIds.contains(db.getLineId())
                && zeroIfNull(db.getIssuedQty()).compareTo(BigDecimal.ZERO) <= 0) {
                lineMapper.deleteById(db.getLineId());
            }
        }
    }

    private void deleteLinesByIssueId(Long issueId) {
        if (issueId != null) {
            LambdaQueryWrapper<WmProductionIssueLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(WmProductionIssueLine::getIssueId, issueId);
            lineMapper.delete(lqw);
        }
    }

    /** 汇总单头总数量/总金额：按"本次领料数量"汇总（未执行/待执行时反映本次计划量，
     *  执行后 current_qty 已清零，此时按"已领数量 issued_qty"反映累计已发量），金额同理取 amount 与单价×已领的较大者 */
    private void recalcHeaderTotals(Long issueId) {
        List<WmProductionIssueLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(WmProductionIssueLine.class)
                .eq(WmProductionIssueLine::getIssueId, issueId));
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (WmProductionIssueLine line : lines) {
            // 优先取本次领料量；执行后为空则取已领量
            BigDecimal qty = line.getCurrentQty() != null ? line.getCurrentQty() : zeroIfNull(line.getIssuedQty());
            BigDecimal price = zeroIfNull(line.getUnitPrice());
            BigDecimal amt = line.getAmount() != null ? line.getAmount() : qty.multiply(price);
            totalQty = totalQty.add(qty);
            totalAmount = totalAmount.add(amt);
        }
        WmProductionIssue update = new WmProductionIssue();
        update.setIssueId(issueId);
        update.setTotalQty(totalQty);
        update.setTotalAmount(totalAmount);
        baseMapper.updateById(update);
    }

    /** 行是否已领满：行状态为 ISSUED，或有应领定额且已领 >= 应领 */
    private boolean isLineFullyIssued(WmProductionIssueLine line) {
        if (LINE_ISSUED.equals(line.getLineStatus())) {
            return true;
        }
        BigDecimal required = zeroIfNull(line.getRequiredQty());
        // 无应领定额的手工行：只要未处于 ISSUED，视为未完成（避免误判单头已完成）
        if (required.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal issued = zeroIfNull(line.getIssuedQty());
        return required.compareTo(issued) <= 0;
    }

    /** 按物料+仓库(可空)+批次(可空)+库位(可空)汇总可用库存，与策略扣减范围一致 */
    private BigDecimal sumAvailable(Long materialId, Long warehouseId, Long batchId, Long locationId) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, materialId);
        lqw.eq(warehouseId != null, WmMaterialStock::getWarehouseId, warehouseId);
        lqw.eq(batchId != null, WmMaterialStock::getBatchId, batchId);
        lqw.eq(locationId != null, WmMaterialStock::getLocationId, locationId);
        // 仅查未冻结库存：frozen_flag 0=未冻结/可用，1=已冻结
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        lqw.gt(WmMaterialStock::getStockQuantity, BigDecimal.ZERO);
        List<WmMaterialStock> list = stockMapper.selectList(lqw);
        BigDecimal sum = BigDecimal.ZERO;
        for (WmMaterialStock s : list) {
            sum = sum.add(zeroIfNull(s.getStockQuantity()));
        }
        return sum;
    }

    /** 回写工单物料需求明细的已发量/欠料量 */
    private void updateRequirementDetail(Long detailId, BigDecimal issuedDelta) {
        if (detailId == null) {
            return;
        }
        PrdMaterialRequirementDetail detail = detailMapper.selectById(detailId);
        if (detail == null) {
            return;
        }
        BigDecimal issued = zeroIfNull(detail.getIssuedQuantity()).add(issuedDelta);
        detail.setIssuedQuantity(issued);
        BigDecimal required = zeroIfNull(detail.getRequiredQuantity());
        detail.setShortageQuantity(required.subtract(issued));
        detailMapper.updateById(detail);
    }

    private BigDecimal zeroIfNull(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private LambdaQueryWrapper<WmProductionIssue> buildQueryWrapper(WmProductionIssueBo bo) {
        LambdaQueryWrapper<WmProductionIssue> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getIssueCode()), WmProductionIssue::getIssueCode, bo.getIssueCode());
        lqw.eq(bo.getWoId() != null, WmProductionIssue::getWoId, bo.getWoId());
        lqw.eq(StringUtils.isNotBlank(bo.getWoNo()), WmProductionIssue::getWoNo, bo.getWoNo());
        lqw.eq(bo.getWarehouseId() != null, WmProductionIssue::getWarehouseId, bo.getWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmProductionIssue::getStatus, bo.getStatus());
        lqw.orderByDesc(WmProductionIssue::getCreateTime);
        return lqw;
    }
}
