package com.nexus.erp.pu.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.domain.*;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseDocumentGuardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 旧写路径的失败关闭保护，不签发认证、不猜历史归属、不改单位/数量或账本。
 * 缺失头部标记不是历史证明：永久 command、当前行身份与全部（含终止/已删除）ledger 均参与识别。
 * 调用方在写事务内使用；支持的认证状态操作必须改走专属生命周期，其他操作明确拒绝。
 */
@Service
@DS("erp")
@RequiredArgsConstructor
public class PuCertifiedPurchaseDocumentGuard {
    private final PuCertifiedPurchaseDocumentGuardMapper mapper;

    public void requireLegacyRequisitionMutation(Long id) {
        if (protectedRequisition(tenant(), requiredId(id))) throw protectedDocument();
    }

    public void requireLegacyRequisitionLineMutation(Long id) {
        if (protectedRequisitionLine(tenant(), requiredId(id))) throw protectedDocument();
    }

    public void requireLegacyRequisitionEdit(Long id, List<PuPurchaseRequisitionLine> proposed) {
        String tenant = tenant();
        id = requiredId(id);
        if (protectedRequisition(tenant, id)) throw protectedDocument();
        if (proposed == null) return;
        for (PuPurchaseRequisitionLine line : proposed) {
            if (line == null) throw unknown();
            if (line.getTenantId() != null && !tenant.equals(line.getTenantId())) throw unknown();
            if (sourceHint(line.getSourceType()) || line.getSourceId() != null) throw protectedDocument();
            if (line.getRequisitionId() != null && !Objects.equals(id, line.getRequisitionId())) throw unknown();
            if (line.getLineId() != null) {
                Long lineId = requiredId(line.getLineId());
                if (protectedRequisitionLine(tenant, lineId)) throw protectedDocument();
                PuPurchaseRequisitionLine persisted = read(() -> mapper.lockRequisitionLine(tenant, lineId));
                if (persisted == null || !Objects.equals(id, persisted.getRequisitionId())) throw unknown();
            }
        }
    }

    public void requireLegacyOrderMutation(Long id) {
        if (protectedOrder(tenant(), requiredId(id))) throw protectedDocument();
    }

    public void requireLegacyOrderLineMutation(Long id) {
        if (protectedOrderLine(tenant(), requiredId(id))) throw protectedDocument();
    }

    public void requireLegacyOrderEdit(Long id, Long proposedRequisitionId, List<PuPurchaseOrderLine> proposed) {
        String tenant = tenant();
        id = requiredId(id);
        if (protectedOrder(tenant, id)) throw protectedDocument();
        if (proposedRequisitionId != null && protectedRequisition(tenant, requiredId(proposedRequisitionId))) {
            throw protectedDocument();
        }
        if (proposed == null) return;
        for (PuPurchaseOrderLine line : proposed) {
            if (line == null) throw unknown();
            if (line.getOrderId() != null && !Objects.equals(id, line.getOrderId())) throw unknown();
            if (line.getRequisitionLineId() != null
                && protectedRequisitionLine(tenant, requiredId(line.getRequisitionLineId()))) throw protectedDocument();
            if (line.getLineId() != null) {
                Long lineId = requiredId(line.getLineId());
                if (protectedOrderLine(tenant, lineId)) throw protectedDocument();
                PuPurchaseOrderLine persisted = read(() -> mapper.lockOrderLine(tenant, lineId));
                if (persisted == null || !Objects.equals(id, persisted.getOrderId())) throw unknown();
            }
        }
    }

    public void requireLegacyReturnMutation(Long id) {
        String tenant = tenant();
        Long returnId = requiredId(id);
        PuPurchaseReturn row = read(() -> mapper.lockReturn(tenant, returnId));
        if (row == null || !Objects.equals(returnId, row.getReturnId())) throw unknown();
        requireLegacyReturnInput(tenant, row.getOrderId(), read(() -> mapper.lockReturnLines(tenant, returnId)));
    }

    public void requireLegacyReturnInput(Long orderId, List<PuPurchaseReturnLine> lines) {
        requireLegacyReturnInput(tenant(), orderId, lines);
    }

    private void requireLegacyReturnInput(String tenant, Long orderId, List<PuPurchaseReturnLine> lines) {
        if (orderId != null && protectedOrder(tenant, requiredId(orderId))) throw protectedDocument();
        if (lines == null || lines.isEmpty()) throw unknown();
        for (PuPurchaseReturnLine line : lines) {
            if (line == null) throw unknown();
            if (line.getTenantId() != null && !tenant.equals(line.getTenantId())) throw unknown();
            if (line.getOrderLineId() != null && protectedOrderLine(tenant, requiredId(line.getOrderLineId()))) {
                throw protectedDocument();
            }
        }
        // 保护检查不等于归属证明：旧订单 A 的退货也不能使用旧订单 B 的行或另一物料。
        Long parentId = requiredId(orderId);
        var parent = read(() -> mapper.lockReturnOrderIdentity(tenant, parentId));
        if (parent == null || !tenant.equals(parent.tenantId()) || !parentId.equals(parent.orderId())) throw unknown();
        for (PuPurchaseReturnLine line : lines) {
            Long lineId = requiredId(line.getOrderLineId());
            Long materialId = requiredId(line.getMaterialId());
            var persisted = read(() -> mapper.lockReturnOrderLineIdentity(tenant, lineId));
            if (persisted == null || !tenant.equals(persisted.tenantId()) || !lineId.equals(persisted.lineId())
                || !parentId.equals(persisted.orderId()) || !materialId.equals(persisted.materialId())) throw unknown();
        }
    }

    private boolean protectedRequisition(String tenant, Long id) {
        if (positive(read(() -> mapper.countRequisitionCommands(tenant, id)))
            || positive(read(() -> mapper.countRequisitionAllocations(tenant, id)))) return true;
        PuPurchaseRequisition row = read(() -> mapper.lockRequisition(tenant, id));
        if (row == null || !Objects.equals(id, row.getRequisitionId())) throw unknown();
        if (sourceHint(row.getSourceType()) || StringUtils.isNotBlank(row.getDecisionIdempotencyKey())
            || row.getDecisionRunId() != null || row.getDecisionActionId() != null) return true;
        List<PuPurchaseRequisitionLine> lines = read(() -> mapper.lockRequisitionLines(tenant, id));
        if (lines == null) throw unknown();
        for (PuPurchaseRequisitionLine line : lines) {
            if (line == null || !Objects.equals(id, line.getRequisitionId())) throw unknown();
            if (sourceHint(line.getSourceType()) || line.getSourceId() != null) return true;
            if (positive(read(() -> mapper.countRequisitionLineAllocations(tenant, requiredId(line.getLineId()))))) return true;
        }
        return false;
    }

    private boolean protectedRequisitionLine(String tenant, Long id) {
        if (positive(read(() -> mapper.countRequisitionLineAllocations(tenant, id)))) return true;
        PuPurchaseRequisitionLine line = read(() -> mapper.lockRequisitionLine(tenant, id));
        if (line == null || !Objects.equals(id, line.getLineId())) throw unknown();
        return sourceHint(line.getSourceType()) || line.getSourceId() != null
            || protectedRequisition(tenant, requiredId(line.getRequisitionId()));
    }

    private boolean protectedOrder(String tenant, Long id) {
        if (positive(read(() -> mapper.countOrderAllocations(tenant, id)))) return true;
        PuPurchaseOrder row = read(() -> mapper.lockOrder(tenant, id));
        if (row == null || !Objects.equals(id, row.getOrderId())) throw unknown();
        if (row.getRequisitionId() != null && protectedRequisition(tenant, requiredId(row.getRequisitionId()))) return true;
        List<PuPurchaseOrderLine> lines = read(() -> mapper.lockOrderLines(tenant, id));
        if (lines == null) throw unknown();
        for (PuPurchaseOrderLine line : lines) {
            if (line == null || !Objects.equals(id, line.getOrderId())) throw unknown();
            if (positive(read(() -> mapper.countOrderLineAllocations(tenant, requiredId(line.getLineId()))))) return true;
            if (line.getRequisitionLineId() != null
                && protectedRequisitionLine(tenant, requiredId(line.getRequisitionLineId()))) return true;
        }
        return false;
    }

    private boolean protectedOrderLine(String tenant, Long id) {
        if (positive(read(() -> mapper.countOrderLineAllocations(tenant, id)))) return true;
        PuPurchaseOrderLine line = read(() -> mapper.lockOrderLine(tenant, id));
        if (line == null || !Objects.equals(id, line.getLineId())) throw unknown();
        return (line.getRequisitionLineId() != null
            && protectedRequisitionLine(tenant, requiredId(line.getRequisitionLineId())))
            || protectedOrder(tenant, requiredId(line.getOrderId()));
    }

    private boolean sourceHint(String value) {
        String source = StringUtils.trimToEmpty(value).toUpperCase(Locale.ROOT);
        return !source.isEmpty() && !"MANUAL".equals(source);
    }

    private boolean positive(Long count) {
        if (count == null || count < 0) throw unknown();
        return count > 0;
    }

    private String tenant() {
        String tenant = TenantHelper.getTenantId();
        if (StringUtils.isBlank(tenant)) throw new ServiceException("租户上下文缺失，无法核验采购来源保护");
        return tenant;
    }

    private Long requiredId(Long id) {
        if (id == null || id <= 0) throw unknown();
        return id;
    }

    private <T> T read(Supplier<T> query) {
        try { return query.get(); }
        catch (RuntimeException error) { throw unknown(); }
    }

    private ServiceException protectedDocument() {
        return new ServiceException("采购来源已认证或尚未核验，旧写路径不可改写来源、数量或状态；新增请使用 /erp/pu/requisition/certified 来源认证入口，已有单据请使用受保护采购生命周期，未支持的操作暂不开放");
    }

    private ServiceException unknown() {
        return new ServiceException("采购来源保护事实未知，禁止按历史单据放行写入；新增来源认证请使用 /erp/pu/requisition/certified，既有单据不可猜测或升级归属");
    }
}
