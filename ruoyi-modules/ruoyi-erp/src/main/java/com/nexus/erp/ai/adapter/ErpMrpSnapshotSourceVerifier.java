package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.RequirementFact;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * 面向未来决策契约、须显式启用的只读 MRP 来源核验。
 * 此 Bean 不会使普通展示快照具备可执行性；P1 写入门禁仍然关闭。
 */
@Component
@DS("erp")
public class ErpMrpSnapshotSourceVerifier {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final PrdMaterialRequirementDetailMapper detailMapper;
    private final PrdMaterialRequirementMapper requirementMapper;
    private final PrdWorkOrderMapper workOrderMapper;
    private final BdMaterialMapper materialMapper;
    private final BdUnitOfMeasureMapper uomMapper;

    public ErpMrpSnapshotSourceVerifier(PrdMaterialRequirementDetailMapper detailMapper,
                                        PrdMaterialRequirementMapper requirementMapper,
                                        PrdWorkOrderMapper workOrderMapper,
                                        BdMaterialMapper materialMapper,
                                        BdUnitOfMeasureMapper uomMapper) {
        this.detailMapper = detailMapper;
        this.requirementMapper = requirementMapper;
        this.workOrderMapper = workOrderMapper;
        this.materialMapper = materialMapper;
        this.uomMapper = uomMapper;
    }

    /**
     * 在同一个 ERP 只读事务内重新读取全部明细标识；来源信息不完整或不一致时拒绝，
     * 不能把未知数量解释为零。调用方仍负责授权、快照新鲜度以及后续任何 v2 门禁。
     *
     * @param snapshot 待核验来源的交付上下文快照
     * @param actor 提供租户身份的调用方上下文，不替代调用方授权核验
     * @return 重新读取并核验通过的需求明细
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ, readOnly = true)
    public List<VerifiedDetail> verify(DeliveryContextSnapshot snapshot, DecisionActor actor) {
        if (actor == null || actor.tenantId() == null || actor.tenantId().isBlank()
            || snapshot == null || snapshot.rootBusinessRef() == null || snapshot.requirements().isEmpty()) {
            throw unverified();
        }
        String tenantId = actor.tenantId();
        Set<Long> detailIds = new HashSet<>();
        for (RequirementFact fact : snapshot.requirements()) {
            if (fact == null || !positive(fact.detailId()) || !positive(fact.materialId())
                || !positive(fact.workOrderId()) || fact.requiredDate() == null
                || fact.requiredQty() == null || fact.requiredQty().signum() <= 0
                || !detailIds.add(fact.detailId())
                || snapshot.workOrders().stream().noneMatch(wo -> Objects.equals(wo.id(), fact.workOrderId()))) {
                throw unverified();
            }
        }
        /** 明细实体未映射 del_flag，因此必须在读取查询中显式约束该字段。 */
        List<PrdMaterialRequirementDetail> fetched = detailMapper.selectList(
            Wrappers.<PrdMaterialRequirementDetail>query()
                .in("detail_id", detailIds)
                .eq("tenant_id", tenantId)
                .eq("del_flag", "0"));
        Map<Long, PrdMaterialRequirementDetail> details = index(fetched, detailIds,
            PrdMaterialRequirementDetail::getDetailId);
        Set<Long> requirementIds = new HashSet<>();
        Set<Long> materialIds = new HashSet<>();
        Set<Long> workOrderIds = new HashSet<>();
        for (RequirementFact fact : snapshot.requirements()) {
            PrdMaterialRequirementDetail detail = details.get(fact.detailId());
            if (detail == null || !tenantId.equals(detail.getTenantId())
                || !positive(detail.getRequirementId()) || !Objects.equals(fact.materialId(), detail.getMaterialId())
                || detail.getRequiredDate() == null
                || !Objects.equals(fact.requiredDate(), Instant.ofEpochMilli(detail.getRequiredDate().getTime())
                    .atZone(BUSINESS_ZONE).toLocalDate())
                || detail.getRequiredQuantity() == null || detail.getIssuedQuantity() == null
                || detail.getRequiredQuantity().signum() <= 0 || detail.getIssuedQuantity().signum() < 0
                || detail.getIssuedQuantity().compareTo(detail.getRequiredQuantity()) >= 0
                || fact.requiredQty().compareTo(detail.getRequiredQuantity().subtract(detail.getIssuedQuantity())) != 0) {
                throw unverified();
            }
            requirementIds.add(detail.getRequirementId());
            materialIds.add(detail.getMaterialId());
            workOrderIds.add(fact.workOrderId());
        }
        Map<Long, PrdMaterialRequirement> headers = index(requirementMapper.selectBatchIds(requirementIds),
            requirementIds, PrdMaterialRequirement::getRequirementId);
        Map<Long, PrdWorkOrder> workOrders = index(workOrderMapper.selectBatchIds(workOrderIds),
            workOrderIds, PrdWorkOrder::getWoId);
        Map<Long, BdMaterial> materials = index(materialMapper.selectBatchIds(materialIds),
            materialIds, BdMaterial::getMaterialId);
        Set<Long> uomIds = new HashSet<>();
        for (Long materialId : materialIds) {
            BdMaterial material = materials.get(materialId);
            if (material == null || !tenantId.equals(material.getTenantId())
                || !positive(material.getBaseUomId()) || !Long.valueOf(1L).equals(material.getStatus())
                || !"0".equals(material.getDelFlag())) {
                throw unverified();
            }
            uomIds.add(material.getBaseUomId());
        }
        Map<Long, BdUnitOfMeasure> uoms = index(uomMapper.selectBatchIds(uomIds),
            uomIds, BdUnitOfMeasure::getUomId);
        List<VerifiedDetail> result = new ArrayList<>();
        for (RequirementFact fact : snapshot.requirements()) {
            PrdMaterialRequirementDetail detail = details.get(fact.detailId());
            PrdMaterialRequirement header = headers.get(detail.getRequirementId());
            PrdWorkOrder workOrder = workOrders.get(fact.workOrderId());
            Long baseUomId = materials.get(fact.materialId()).getBaseUomId();
            BdUnitOfMeasure uom = uoms.get(baseUomId);
            if (header == null || !tenantId.equals(header.getTenantId())
                || workOrder == null || !tenantId.equals(workOrder.getTenantId())
                || !Objects.equals(header.getPlanId(), workOrder.getPlanId())
                || header.getPlanId() == null
                || ("MATERIAL_REQUIREMENT".equalsIgnoreCase(workOrder.getSourceType())
                    && !Objects.equals(workOrder.getSourceId(), header.getRequirementId()))
                || uom == null || !tenantId.equals(uom.getTenantId())
                || !Long.valueOf(1L).equals(uom.getStatus()) || !"0".equals(uom.getDelFlag())
                || !Objects.equals(baseUomId, decimalUnitId(detail.getUnit()))) {
                throw unverified();
            }
            result.add(new VerifiedDetail(detail.getDetailId(), detail.getRequirementId(),
                fact.workOrderId(), fact.materialId(), fact.requiredDate(), baseUomId, fact.requiredQty()));
        }
        result.sort(Comparator.comparing(VerifiedDetail::detailId));
        return List.copyOf(result);
    }

    private static Long decimalUnitId(String unit) {
        if (unit == null || !unit.matches("[1-9][0-9]*")) {
            return null;
        }
        try {
            long id = Long.parseLong(unit);
            return id > 0 && Long.toString(id).equals(unit) ? id : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean positive(Long id) {
        return id != null && id > 0;
    }

    private static <T> Map<Long, T> index(Collection<T> rows, Set<Long> expected, Function<T, Long> idOf) {
        if (rows == null || rows.size() != expected.size()) {
            throw unverified();
        }
        Map<Long, T> found = new HashMap<>();
        for (T row : rows) {
            if (row == null || !expected.contains(idOf.apply(row)) || found.putIfAbsent(idOf.apply(row), row) != null) {
                throw unverified();
            }
        }
        return found;
    }

    private static ServiceException unverified() {
        return new ServiceException("MRP 来源、租户、数量或基本单位无法核验，禁止生成可执行采购动作");
    }

    /** 即使工单、物料和日期相同，每个来源仍保持独立。 */
    public record VerifiedDetail(Long detailId, Long requirementId, Long workOrderId, Long materialId,
                                 LocalDate requiredDate, Long baseUomId, BigDecimal unissuedDemandBase) {
    }
}
