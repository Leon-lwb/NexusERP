package com.nexus.erp.prd.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.vo.PickingAllocation;
import com.nexus.erp.wm.mapper.WmProductionIssueLineMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueMapper;
import com.nexus.erp.wm.service.IPickingResolutionService;
import com.nexus.erp.wm.service.IWmStockOperationService;
import com.nexus.system.strategy.AutoCodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 生产倒冲服务实现
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PrdBackflushServiceImpl {

    private final PrdWorkOrderMapper workOrderMapper;
    private final PrdMaterialRequirementMapper requirementMapper;
    private final PrdMaterialRequirementDetailMapper detailMapper;
    private final WmProductionIssueMapper productionIssueMapper;
    private final WmProductionIssueLineMapper productionIssueLineMapper;
    private final IWmStockOperationService stockOperationService;
    private final IPickingResolutionService pickingResolutionService;
    private final AutoCodeUtil autoCodeUtil;

    /**
     * 工单完工倒冲领料（方案A：工单级倒冲）
     *
     * @param woId          工单ID
     * @param completionQty 本次完工数量
     */
    @Transactional(rollbackFor = Exception.class)
    public void backflushOnCompletion(Long woId, BigDecimal completionQty) {
        if (woId == null || completionQty == null || completionQty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // 1. 查询工单
        PrdWorkOrder wo = workOrderMapper.selectById(woId);
        if (wo == null) {
            throw new ServiceException("工单不存在");
        }
        if (wo.getBackflushFlag() == null || wo.getBackflushFlag() != 1) {
            log.debug("工单[{}]非倒冲工单，跳过倒冲", wo.getWoNo());
            return;
        }

        // 2. 查询物料需求明细（倒冲物料）
        List<PrdMaterialRequirementDetail> backflushDetails = queryBackflushDetails(woId);
        if (backflushDetails.isEmpty()) {
            log.warn("工单[{}]无倒冲物料需求明细", wo.getWoNo());
            return;
        }

        // 3. 创建生产领料单头（记录倒冲痕迹）
        WmProductionIssue issue = createProductionIssueHeader(wo, completionQty);

        // 4. 按BOM定额计算消耗并执行库存扣减
        Date now = new Date();
        List<WmProductionIssueLine> lines = new ArrayList<>();

        for (PrdMaterialRequirementDetail detail : backflushDetails) {
            // 消耗量 = 完工数量 × BOM单位用量 × (1 + 损耗率/100)
            BigDecimal bomQty = detail.getBomQuantity() != null ? detail.getBomQuantity() : BigDecimal.ONE;
            BigDecimal lossRate = detail.getLossRate() != null ? detail.getLossRate() : BigDecimal.ZERO;
            BigDecimal consumeQty = completionQty
                .multiply(bomQty)
                .multiply(BigDecimal.ONE.add(lossRate.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)))
                .setScale(4, RoundingMode.HALF_UP);

            // 按拣货规则解析目标仓库与选中库存（不足即抛异常带具体仓库）
            PickingAllocation alloc = pickingResolutionService.resolvePicking(detail.getMaterialId(), consumeQty);

            // 创建领料行（回写仓库）
            WmProductionIssueLine line = new WmProductionIssueLine();
            line.setIssueId(issue.getIssueId());
            line.setWarehouseId(alloc.getWarehouseId());
            line.setWarehouseCode(alloc.getWarehouseCode());
            line.setWarehouseName(alloc.getWarehouseName());
            line.setMaterialId(detail.getMaterialId());
            line.setMaterialCode(detail.getMaterialCode());
            line.setMaterialName(detail.getMaterialName());
            line.setMaterialSpec(detail.getMaterialSpec());
            line.setBaseUomId(detail.getUnit());
            line.setQuantity(consumeQty);
            productionIssueLineMapper.insert(line);
            lines.add(line);

            // 按已分配库存逐条扣减
            stockOperationService.postProductionIssueAllocated(issue.getIssueId(), line.getLineId(), alloc.getStocks(), now);

            // 回写实际消耗
            detail.setActualConsumedQty(
                detail.getActualConsumedQty() != null
                    ? detail.getActualConsumedQty().add(consumeQty)
                    : consumeQty);
            detailMapper.updateById(detail);
        }

        // 5. 更新领料单状态为已完成
        issue.setStatus("COMPLETED");
        productionIssueMapper.updateById(issue);

        log.info("工单[{}]完工倒冲完成，完工数量:{}，倒冲物料:{}种",
            wo.getWoNo(), completionQty.stripTrailingZeros().toPlainString(), lines.size());
    }

    /**
     * 查询工单的倒冲物料需求明细
     */
    private List<PrdMaterialRequirementDetail> queryBackflushDetails(Long woId) {
        PrdWorkOrder wo = workOrderMapper.selectById(woId);
        if (wo == null || wo.getPlanId() == null) {
            return new ArrayList<>();
        }

        // 根据生产计划查询物料需求单
        LambdaQueryWrapper<PrdMaterialRequirement> reqLqw = Wrappers.lambdaQuery();
        reqLqw.eq(PrdMaterialRequirement::getPlanId, wo.getPlanId());
        List<PrdMaterialRequirement> reqs = requirementMapper.selectList(reqLqw);

        List<PrdMaterialRequirementDetail> result = new ArrayList<>();
        for (PrdMaterialRequirement req : reqs) {
            LambdaQueryWrapper<PrdMaterialRequirementDetail> detailLqw = Wrappers.lambdaQuery();
            detailLqw.eq(PrdMaterialRequirementDetail::getRequirementId, req.getRequirementId());
            // 工单级倒冲：工单 backflushFlag=1 时倒冲所有 BOM 子件（明细 backflush_flag 不再作为过滤条件）
            List<PrdMaterialRequirementDetail> details = detailMapper.selectList(detailLqw);
            result.addAll(details);
        }
        return result;
    }

    /**
     * 创建倒冲生产领料单头
     */
    private WmProductionIssue createProductionIssueHeader(PrdWorkOrder wo, BigDecimal completionQty) {
        WmProductionIssue issue = new WmProductionIssue();
        issue.setIssueCode(autoCodeUtil.genSerialCode("WM_PROD_ISSUE_CODE", null));
        issue.setWoId(wo.getWoId());
        issue.setWoNo(wo.getWoNo());
        issue.setIssueDate(new Date());
        issue.setStatus("BACKFLUSH"); // 倒冲类型
        issue.setRemark("完工倒冲领料，完工数量:" + completionQty.stripTrailingZeros().toPlainString());
        productionIssueMapper.insert(issue);
        return issue;
    }
}
