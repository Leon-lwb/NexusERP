package com.nexus.erp.wm.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.wm.domain.WmSalesIssue;
import com.nexus.erp.wm.domain.WmSalesIssueLine;
import com.nexus.erp.wm.mapper.SalesIssueSourceGuardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** 实际销售出库的扣库前来源门禁，不改变审批、订单创建或履行策略。 */
@Service
@DS("erp")
@RequiredArgsConstructor
public class SalesIssueSourceGuard {
    private final SalesIssueSourceGuardMapper mapper;

    /**
     * 在原 ERP 事务内校验所有明细，按头到有序来源行加锁，并按同一来源行合计校验剩余量。
     * <p>返回的值就是锁定查询得到的行，调用方须复用其预留量，不得扣库时再作快照重读。
     * 锁随调用方事务结束释放；独立出库允许没有订单，但任何来源行引用均应拒绝。</p>
     *
     * @param issue 当前销售出库单
     * @param details 当前出库的完整明细
     * @return 来源行 ID 到锁内行的映射；独立出库返回空映射
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Map<Long, SoSalesOrderLine> validateAndLock(WmSalesIssue issue, List<WmSalesIssueLine> details) {
        String tenantId = TenantHelper.getTenantId();
        if (issue == null || StringUtils.isBlank(tenantId) || !tenantId.equals(issue.getTenantId())
            || issue.getIssueId() == null || !"0".equals(issue.getDelFlag())) {
            throw new ServiceException("销售出库单租户或有效性不符");
        }
        if (details == null || details.isEmpty()) {
            throw new ServiceException("出库单明细为空");
        }
        Map<Long, BigDecimal> quantities = new TreeMap<>();
        Set<Long> detailIds = new HashSet<>();
        for (WmSalesIssueLine detail : details) {
            if (detail == null || detail.getLineId() == null || detail.getLineId() <= 0
                || !detailIds.add(detail.getLineId()) || !Objects.equals(detail.getIssueId(), issue.getIssueId())
                || !tenantId.equals(detail.getTenantId()) || !"0".equals(detail.getDelFlag())
                || detail.getMaterialId() == null || detail.getMaterialId() <= 0
                || detail.getQuantityIssue() == null || detail.getQuantityIssue().signum() <= 0) {
                throw new ServiceException("销售出库明细无效或出库数量必须大于零");
            }
            if (issue.getSoId() == null) {
                if (detail.getSourceDocLineId() != null) {
                    throw new ServiceException("独立销售出库不得引用销售订单来源行");
                }
            } else {
                if (detail.getSourceDocLineId() == null || detail.getSourceDocLineId() <= 0) {
                    throw new ServiceException("关联销售出库必须指定有效销售订单来源行");
                }
                quantities.merge(detail.getSourceDocLineId(), detail.getQuantityIssue(), BigDecimal::add);
            }
        }
        if (issue.getSoId() == null) {
            return Map.of();
        }

        SoSalesOrderHeader header = mapper.lockHeader(tenantId, issue.getSoId());
        if (header == null || !tenantId.equals(header.getTenantId())
            || !Objects.equals(header.getSoId(), issue.getSoId()) || !"0".equals(header.getDelFlag())
            || !"CONFIRMED".equals(header.getOrderStatus())) {
            throw new ServiceException("销售订单不存在、无效或未处于已确认状态");
        }
        if (issue.getClientId() == null || !issue.getClientId().equals(header.getClientId())) {
            throw new ServiceException("销售出库客户与销售订单客户不一致");
        }
        List<SoSalesOrderLine> locked = mapper.lockLines(tenantId, issue.getSoId(), new ArrayList<>(quantities.keySet()));
        Map<Long, SoSalesOrderLine> sources = new HashMap<>();
        if (locked == null || locked.size() != quantities.size()) {
            throw new ServiceException("销售订单来源行缺失或不属于当前租户和订单");
        }
        for (SoSalesOrderLine source : locked) {
            if (source == null || source.getLineId() == null || !quantities.containsKey(source.getLineId())
                || sources.putIfAbsent(source.getLineId(), source) != null || !tenantId.equals(source.getTenantId())
                || !Objects.equals(source.getSoId(), issue.getSoId()) || !"0".equals(source.getDelFlag())
                || "CANCELLED".equals(source.getLineStatus())) {
                throw new ServiceException("销售订单来源行无效、已取消或归属不符");
            }
            if (!"QUALIFIED".equals(source.getOqcStatus())) {
                throw new ServiceException("销售订单来源行出货检验尚未合格");
            }
            if (source.getDeliveredQty() == null) {
                throw new ServiceException("销售订单来源行已发货数量为空，请先核实可信来源");
            }
            BigDecimal delivered = source.getDeliveredQty();
            if (source.getOrderedQty() == null || delivered.signum() < 0
                || quantities.get(source.getLineId()).compareTo(source.getOrderedQty().subtract(delivered)) > 0) {
                throw new ServiceException("同一销售订单来源行合计出库数量超过剩余未发数量");
            }
        }
        for (WmSalesIssueLine detail : details) {
            if (!Objects.equals(detail.getMaterialId(), sources.get(detail.getSourceDocLineId()).getMaterialId())) {
                throw new ServiceException("销售出库物料与销售订单来源行物料不一致");
            }
        }
        return sources;
    }
}
