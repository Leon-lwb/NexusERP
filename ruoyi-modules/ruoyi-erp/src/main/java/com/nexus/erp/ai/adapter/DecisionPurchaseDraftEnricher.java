package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.pu.domain.PuPurchaseRequisitionLine;
import com.nexus.erp.pu.domain.bo.PuPurchaseRequisitionBo;
import com.nexus.erp.pu.domain.vo.PuPurchaseRequisitionVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 从租户内主数据补充展示元数据，不使用模型生成值或猜测单位。
 */
@Component
@RequiredArgsConstructor
@DS("erp")
public class DecisionPurchaseDraftEnricher {
    private final BdMaterialMapper materials;
    private final BdUnitOfMeasureMapper units;
    private final DecisionOperatorProfileResolver operators;

    public void enrich(PuPurchaseRequisitionBo draft, DecisionActor actor) {
        var operator = operators.resolve(actor);
        if (draft == null || draft.getPuPurchaseRequisitionLineList() == null)
            throw new ServiceException("采购申请缺少明细");
        draft.setApplicantId(operator.userId());
        draft.setApplicantName(operator.userName());
        draft.setDeptId(operator.deptId());
        draft.setDeptName(operator.deptName());
        draft.setCreateDept(operator.deptId());
        draft.setCreateBy(operator.userId());
        draft.setUpdateBy(operator.userId());
        for (PuPurchaseRequisitionLine line : draft.getPuPurchaseRequisitionLineList()) {
            BdMaterial material = material(line.getMaterialId(), actor);
            if (line.getMaterialCode() != null && !line.getMaterialCode().equals(material.getMaterialCode())
                || line.getMaterialName() != null && !line.getMaterialName().equals(material.getMaterialName()))
                throw new ServiceException("已批准的物料编码或名称与主数据不一致");
            line.setMaterialCode(material.getMaterialCode());
            line.setMaterialName(material.getMaterialName());
            line.setMaterialSpec(material.getMaterialSpec());
            line.setUnit(baseUnit(material, actor));
            List<String> missing = new ArrayList<>();
            if (material.getMaterialSpec() == null || material.getMaterialSpec().isBlank()) missing.add("规格");
            if (line.getUnit() == null) missing.add("基本计量单位");
            if (!missing.isEmpty()) line.setRemark("物料" + String.join("、", missing) + "未维护，请完善物料档案；未进行单位换算");
            line.setTenantId(actor.tenantId());
            line.setCreateDept(operator.deptId());
            line.setCreateBy(operator.userId());
            line.setUpdateBy(operator.userId());
        }
    }

    /**
     * 除冻结数值意图外，还独立核验补充字段。
     */
    public boolean matchesPersisted(PuPurchaseRequisitionVo draft, DecisionActor actor) {
        if (draft == null || draft.getPuPurchaseRequisitionLineList() == null) return false;
        var operator = operators.resolve(actor);
        if (!Objects.equals(draft.getDeptId(), operator.deptId())
            || !Objects.equals(draft.getDeptName(), operator.deptName())
            || !Objects.equals(draft.getCreateDept(), operator.deptId())
            || !Objects.equals(draft.getCreateBy(), operator.userId())) return false;
        for (PuPurchaseRequisitionLine line : draft.getPuPurchaseRequisitionLineList()) {
            BdMaterial material = material(line.getMaterialId(), actor);
            if (!Objects.equals(line.getMaterialCode(), material.getMaterialCode())
                || !Objects.equals(line.getMaterialName(), material.getMaterialName())
                || !Objects.equals(line.getMaterialSpec(), material.getMaterialSpec())
                || !Objects.equals(line.getUnit(), baseUnit(material, actor))
                || !Objects.equals(line.getTenantId(), actor.tenantId())
                || !Objects.equals(line.getCreateDept(), operator.deptId())
                || !Objects.equals(line.getCreateBy(), operator.userId())
                || !Objects.equals(line.getUpdateBy(), operator.userId())) return false;
        }
        return true;
    }

    private BdMaterial material(Long id, DecisionActor actor) {
        BdMaterial material = id == null ? null : materials.selectById(id);
        if (material == null || !Objects.equals(material.getMaterialId(), id)
            || !Objects.equals(material.getTenantId(), actor.tenantId()) || !Long.valueOf(1).equals(material.getStatus())
            || material.getMaterialCode() == null || material.getMaterialCode().isBlank()
            || material.getMaterialName() == null || material.getMaterialName().isBlank())
            throw new ServiceException("采购物料不存在、已停用或不属于当前租户");
        return material;
    }

    private String baseUnit(BdMaterial material, DecisionActor actor) {
        // 缺料量以基本单位计量；直接使用采购单位会悄然改变数量含义。
        if (material.getBaseUomId() == null) return null;
        BdUnitOfMeasure unit = units.selectById(material.getBaseUomId());
        if (unit == null || !Objects.equals(unit.getUomId(), material.getBaseUomId())
            || !Objects.equals(unit.getTenantId(), actor.tenantId()) || !Long.valueOf(1).equals(unit.getStatus())
            || unit.getUomName() == null || unit.getUomName().isBlank())
            throw new ServiceException("采购物料基本单位不存在、已停用或不属于当前租户");
        return unit.getUomName();
    }
}
