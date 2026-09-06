package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.erp.prd.domain.PrdSubcontract;
import com.nexus.erp.prd.domain.PrdSubcontractBomLine;
import com.nexus.erp.prd.mapper.PrdSubcontractBomLineMapper;
import com.nexus.erp.prd.mapper.PrdSubcontractMapper;
import com.nexus.erp.prd.service.IPrdSubcontractClosureService;
import com.nexus.erp.prd.service.IPrdSubcontractService;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmSubcontractIssue;
import com.nexus.erp.wm.domain.WmSubcontractIssueLine;
import com.nexus.erp.wm.domain.bo.WmSubcontractIssueBo;
import com.nexus.erp.wm.domain.bo.WmSubcontractIssueLineBo;
import com.nexus.erp.wm.domain.vo.AllocatedStock;
import com.nexus.erp.wm.domain.vo.WmSubcontractIssueVo;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmSubcontractIssueLineMapper;
import com.nexus.erp.wm.mapper.WmSubcontractIssueMapper;
import com.nexus.erp.wm.service.IWmSubcontractIssueService;
import com.nexus.erp.wm.strategy.StockOperationStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 委外发料单 Service 实现 (PCBA 闭环)
 * <p>
 * 备料建议(FEFO/FIFO) -> 发料单(行级分配明细, 可手调批次) -> 过账(订单维度分布式锁 +
 * 乐观锁扣减 + 移动平均成本结转 + 快照行累加) -> 全部自供行发齐自动推进订单。
 * <p>数据源: 发料单与库存均在 ry-erp 库, {@code @DS("erp")}。</p>
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmSubcontractIssueServiceImpl implements IWmSubcontractIssueService {

    private final WmSubcontractIssueMapper issueMapper;
    private final WmSubcontractIssueLineMapper issueLineMapper;
    private final PrdSubcontractMapper subcontractMapper;
    private final PrdSubcontractBomLineMapper bomLineMapper;
    private final WmMaterialStockMapper stockMapper;
    private final StockOperationStrategyFactory strategyFactory;
    private final IPrdSubcontractClosureService closureService;
    private final LockTemplate lockTemplate;

    @Override
    public List<WmSubcontractIssueVo> listBySubcontract(Long subcontractId) {
        return issueMapper.selectVoList(Wrappers.<WmSubcontractIssue>lambdaQuery()
            .eq(WmSubcontractIssue::getSubcontractId, subcontractId)
            .orderByDesc(WmSubcontractIssue::getCreateTime));
    }

    @Override
    public Map<String, Object> selectById(Long issueId) {
        WmSubcontractIssue issue = issueMapper.selectById(issueId);
        if (issue == null) {
            throw new ServiceException("发料单不存在: " + issueId);
        }
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("issue", issueMapper.selectVoById(issueId));
        result.put("lines", issueLineMapper.selectList(Wrappers.<WmSubcontractIssueLine>lambdaQuery()
            .eq(WmSubcontractIssueLine::getIssueId, issueId)
            .orderByAsc(WmSubcontractIssueLine::getLineId)));
        return result;
    }

    @Override
    public List<Map<String, Object>> suggestAllocations(Long subcontractId) {
        PrdSubcontract subcontract = subcontractMapper.selectById(subcontractId);
        if (subcontract == null) {
            throw new ServiceException("委外订单不存在");
        }
        List<PrdSubcontractBomLine> bomLines = bomLineMapper.selectList(Wrappers.<PrdSubcontractBomLine>lambdaQuery()
            .eq(PrdSubcontractBomLine::getSubcontractId, subcontractId)
            .eq(PrdSubcontractBomLine::getSupplyType, IPrdSubcontractClosureService.SUPPLY_SELF)
            .orderByAsc(PrdSubcontractBomLine::getLineId));

        List<Map<String, Object>> result = new ArrayList<>(bomLines.size());
        for (PrdSubcontractBomLine bl : bomLines) {
            BigDecimal demand = bl.getDemandQty() == null ? BigDecimal.ZERO : bl.getDemandQty();
            BigDecimal issued = bl.getIssuedQty() == null ? BigDecimal.ZERO : bl.getIssuedQty();
            BigDecimal remaining = demand.subtract(issued).max(BigDecimal.ZERO);

            Map<String, Object> row = new LinkedHashMap<>(10);
            row.put("bomLineId", bl.getLineId());
            row.put("materialId", bl.getMaterialId());
            row.put("materialCode", bl.getMaterialCode());
            row.put("materialName", bl.getMaterialName());
            row.put("materialSpec", bl.getMaterialSpec());
            row.put("baseUomName", bl.getBaseUomName());
            row.put("demandQty", demand);
            row.put("issuedQty", issued);
            row.put("remainingQty", remaining);

            List<Map<String, Object>> allocations = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                // FEFO(先过期) -> FIFO(先入库), 仅可用量(排除冻结与已预留)
                List<WmMaterialStock> stocks = stockMapper.selectList(
                    Wrappers.<WmMaterialStock>lambdaQuery()
                        .eq(WmMaterialStock::getMaterialId, bl.getMaterialId())
                        .eq(WmMaterialStock::getFrozenFlag, 0L)
                        .apply("(stock_quantity - IFNULL(reserved_quantity, 0)) > 0")
                        .orderByAsc(WmMaterialStock::getExpireDate, WmMaterialStock::getRecptDate,
                            WmMaterialStock::getStockId));
                BigDecimal need = remaining;
                for (WmMaterialStock s : stocks) {
                    if (need.compareTo(BigDecimal.ZERO) <= 0) {
                        break;
                    }
                    BigDecimal available = (s.getStockQuantity() == null ? BigDecimal.ZERO : s.getStockQuantity())
                        .subtract(s.getReservedQuantity() == null ? BigDecimal.ZERO : s.getReservedQuantity());
                    if (available.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    BigDecimal deduct = available.min(need);
                    Map<String, Object> a = new LinkedHashMap<>(6);
                    a.put("stockId", s.getStockId());
                    a.put("warehouseId", s.getWarehouseId());
                    a.put("warehouseName", s.getWarehouseName());
                    a.put("locationId", s.getLocationId());
                    a.put("batchId", s.getBatchId());
                    a.put("batchCode", s.getBatchCode());
                    a.put("availableQty", available);
                    a.put("deductQty", deduct);
                    allocations.add(a);
                    total = total.add(deduct);
                    need = need.subtract(deduct);
                }
            }
            row.put("allocations", allocations);
            row.put("allocatedQty", total);
            row.put("shortage", remaining.subtract(total).max(BigDecimal.ZERO));
            result.add(row);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createIssue(WmSubcontractIssueBo bo) {
        PrdSubcontract subcontract = subcontractMapper.selectById(bo.getSubcontractId());
        if (subcontract == null) {
            throw new ServiceException("委外订单不存在");
        }
        String status = subcontract.getStatus();
        if (!IPrdSubcontractService.STATUS_SUBMITTED.equals(status)
            && !IPrdSubcontractService.STATUS_APPROVED.equals(status)) {
            throw new ServiceException("仅已提交/已批准状态可创建发料单, 当前状态: " + status);
        }
        if (bo.getLines() == null || bo.getLines().isEmpty()) {
            throw new ServiceException("发料单必须包含至少一行明细");
        }
        for (WmSubcontractIssueLineBo lb : bo.getLines()) {
            if (lb.getBomLineId() == null) {
                throw new ServiceException("发料行必须关联BOM快照行");
            }
            if (lb.getIssueQty() == null || lb.getIssueQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ServiceException("发料量必须大于0: " + lb.getMaterialName());
            }
            PrdSubcontractBomLine bl = bomLineMapper.selectById(lb.getBomLineId());
            if (bl == null || !subcontractIdEqual(bl.getSubcontractId(), bo.getSubcontractId())) {
                throw new ServiceException("BOM快照行不属于该委外订单: " + lb.getBomLineId());
            }
            // 超发校验: 累计已发 + 本次 <= 需求量
            BigDecimal issued = bl.getIssuedQty() == null ? BigDecimal.ZERO : bl.getIssuedQty();
            BigDecimal demand = bl.getDemandQty() == null ? BigDecimal.ZERO : bl.getDemandQty();
            if (issued.add(lb.getIssueQty()).compareTo(demand) > 0) {
                throw new ServiceException("物料【" + lb.getMaterialName() + "】累计发料将超过需求量, 已发"
                    + issued.stripTrailingZeros().toPlainString() + ", 需求"
                    + demand.stripTrailingZeros().toPlainString() + ", 本次"
                    + lb.getIssueQty().stripTrailingZeros().toPlainString());
            }
        }

        WmSubcontractIssue issue = MapstructUtils.convert(bo, WmSubcontractIssue.class);
        issue.setIssueId(null);
        issue.setIssueNo("WSCI-" + subcontract.getSubcontractNo() + "-"
            + (issueMapper.selectCount(Wrappers.<WmSubcontractIssue>lambdaQuery()
                .eq(WmSubcontractIssue::getSubcontractId, bo.getSubcontractId())).intValue() + 1));
        issue.setStatus("DRAFT");
        issue.setSubcontractNo(subcontract.getSubcontractNo());
        BigDecimal totalQty = bo.getLines().stream()
            .map(WmSubcontractIssueLineBo::getIssueQty)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        issue.setTotalQty(totalQty);
        issueMapper.insert(issue);

        for (WmSubcontractIssueLineBo lb : bo.getLines()) {
            WmSubcontractIssueLine line = MapstructUtils.convert(lb, WmSubcontractIssueLine.class);
            line.setLineId(null);
            line.setIssueId(issue.getIssueId());
            line.setSubcontractId(bo.getSubcontractId());
            line.setSubcontractNo(issue.getSubcontractNo());
            issueLineMapper.insert(line);
        }
        log.info("委外发料单创建: {} (订单{}), 行数={}", issue.getIssueNo(),
            subcontract.getSubcontractNo(), bo.getLines().size());
        return issue.getIssueId();
    }

    private boolean subcontractIdEqual(Long a, Long b) {
        return a != null && a.equals(b);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteIssue(Long issueId) {
        WmSubcontractIssue issue = issueMapper.selectById(issueId);
        if (issue == null) {
            return 0;
        }
        if (!"DRAFT".equals(issue.getStatus())) {
            throw new ServiceException("仅草稿状态可删除发料单");
        }
        issueLineMapper.delete(Wrappers.<WmSubcontractIssueLine>lambdaQuery()
            .eq(WmSubcontractIssueLine::getIssueId, issueId));
        return issueMapper.deleteById(issueId);
    }

    @Override
    public void postIssue(Long issueId) {
        // 无事务入口: 先查单取订单维度锁键, 再经代理执行带事务的过账(锁覆盖事务直至提交)
        WmSubcontractIssue issue = issueMapper.selectById(issueId);
        if (issue == null) {
            throw new ServiceException("发料单不存在: " + issueId);
        }
        LockInfo lock = lockTemplate.lock("wm:sc:issue:" + issue.getSubcontractId(), 30000L, 5000L);
        if (lock == null) {
            throw new ServiceException("委外订单发料操作并发冲突, 请稍后重试");
        }
        try {
            SpringUtils.getAopProxy(this).doPostIssue(issueId);
        } finally {
            lockTemplate.releaseLock(lock);
        }
    }

    /**
     * 过账事务体: CAS 认领 -> 逐行扣库存(策略) -> 快照行累加 -> 发齐自动推进订单
     */
    @Transactional(rollbackFor = Exception.class)
    public void doPostIssue(Long issueId) {
        WmSubcontractIssue issue = issueMapper.selectById(issueId);
        if (issue == null) {
            throw new ServiceException("发料单不存在");
        }
        // CAS 认领: DRAFT -> POSTED, 多实例/重复点击仅一方生效
        int claimed = issueMapper.update(null, Wrappers.<WmSubcontractIssue>lambdaUpdate()
            .set(WmSubcontractIssue::getStatus, "POSTED")
            .set(WmSubcontractIssue::getIssueDate, new Date())
            .eq(WmSubcontractIssue::getIssueId, issueId)
            .eq(WmSubcontractIssue::getStatus, "DRAFT"));
        if (claimed == 0) {
            log.warn("发料单[{}]已被认领过账, 跳过", issue.getIssueNo());
            return;
        }

        Date now = new Date();
        List<WmSubcontractIssueLine> lines = issueLineMapper.selectList(
            Wrappers.<WmSubcontractIssueLine>lambdaQuery()
                .eq(WmSubcontractIssueLine::getIssueId, issueId)
                .orderByAsc(WmSubcontractIssueLine::getLineId));
        for (WmSubcontractIssueLine line : lines) {
            List<AllocatedStock> allocs = parseAllocations(line.getAllocJson());
            // 策略扣库存 + 写流水(乐观锁, 失败抛异常回滚整体)
            strategyFactory.getStrategy(StockOperationTypeEnum.SUBCONTRACT_ISSUE)
                .execute(line, allocs, now);
            // 快照行已发量累加(条件更新防负)
            if (line.getBomLineId() != null) {
                bomLineMapper.update(null, Wrappers.<PrdSubcontractBomLine>lambdaUpdate()
                    .setSql("issued_qty = IFNULL(issued_qty, 0) + " + line.getIssueQty().toPlainString())
                    .eq(PrdSubcontractBomLine::getLineId, line.getBomLineId()));
            }
        }
        log.info("委外发料单[{}]过账完成, 行数={}", issue.getIssueNo(), lines.size());

        // 自供行全部发齐 -> 推进订单(RELEASED + 工单 + SN MQ), 同事务
        if (allSelfSupplyLinesFulfilled(issue.getSubcontractId())) {
            closureService.advanceToReleased(issue.getSubcontractId());
        }
    }

    /** 自备料行是否全部发齐(=demand), 无自备料行视为已发齐(纯 turnkey) */
    private boolean allSelfSupplyLinesFulfilled(Long subcontractId) {
        List<PrdSubcontractBomLine> selfLines = bomLineMapper.selectList(
            Wrappers.<PrdSubcontractBomLine>lambdaQuery()
                .eq(PrdSubcontractBomLine::getSubcontractId, subcontractId)
                .eq(PrdSubcontractBomLine::getSupplyType, IPrdSubcontractClosureService.SUPPLY_SELF));
        for (PrdSubcontractBomLine bl : selfLines) {
            BigDecimal demand = bl.getDemandQty() == null ? BigDecimal.ZERO : bl.getDemandQty();
            BigDecimal issued = bl.getIssuedQty() == null ? BigDecimal.ZERO : bl.getIssuedQty();
            if (issued.compareTo(demand) < 0) {
                return false;
            }
        }
        return true;
    }

    private List<AllocatedStock> parseAllocations(String allocJson) {
        if (StringUtils.isBlank(allocJson)) {
            return List.of();
        }
        try {
            return JsonUtils.parseObject(allocJson, new TypeReference<List<AllocatedStock>>() {
            });
        } catch (Exception e) {
            throw new ServiceException("分配明细JSON解析失败: " + e.getMessage());
        }
    }
}
