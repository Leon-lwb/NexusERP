package com.nexus.erp.wm.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.erp.wm.domain.WmBatch;
import com.nexus.erp.wm.domain.bo.WmBatchBo;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.trace.ErpTraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;
import java.util.Set;

/** Registered batch identity and source facts are immutable; only metadata has a CRUD write path. */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
public class WmBatchWriteGuard {
    private static final Set<String> METADATA_STATUSES = Set.of("ACTIVE", "EXPIRED", "LOCKED", "INACTIVE", "CLOSED");
    private final WmBatchMapper batches;
    private final ErpTraceContext context;

    /** Registration declares a batch, never a receipt/issue/QC result or an inventory effect. */
    public WmBatch registration(WmBatchBo bo) {
        String tenant = writeTenant();
        if (bo == null || bo.getMaterialId() == null) throw new ServiceException("物料ID不能为空");
        if (bo.getBatchId() != null) throw new ServiceException("新增批次不能指定已登记身份ID");
        if (bo.getDelFlag() != null && !"0".equals(bo.getDelFlag())) throw new ServiceException("新批次必须保留有效登记身份");
        if (nonzero(bo.getReceivedQty()) || nonzero(bo.getIssuedQty())) {
            throw new ServiceException("新批次已收、已发数量必须为0，累计事实请通过来源单据过账");
        }
        if (bo.getQuantity() != null && bo.getQuantity().signum() < 0) throw new ServiceException("批次登记数量不得为负");
        String status = StringUtils.isBlank(bo.getStatus()) ? "ACTIVE" : bo.getStatus();
        requireStatus(status);
        // Explicit registration projection excludes client audit fields, QC, deletion and cumulative facts.
        WmBatch entity = new WmBatch();
        entity.setTenantId(tenant);
        entity.setBatchCode(bo.getBatchCode());
        entity.setMaterialId(bo.getMaterialId());
        entity.setMaterialCode(bo.getMaterialCode());
        entity.setMaterialName(bo.getMaterialName());
        entity.setMaterialSpec(bo.getMaterialSpec());
        entity.setVendorBatchCode(bo.getVendorBatchCode());
        entity.setVendorId(bo.getVendorId());
        entity.setVendorCode(bo.getVendorCode());
        entity.setVendorName(bo.getVendorName());
        entity.setWarehouseId(bo.getWarehouseId());
        entity.setWarehouseCode(bo.getWarehouseCode());
        entity.setWarehouseName(bo.getWarehouseName());
        entity.setProductionDate(bo.getProductionDate());
        entity.setExpiryDate(bo.getExpiryDate());
        entity.setQuantity(bo.getQuantity() == null ? BigDecimal.ZERO : bo.getQuantity());
        entity.setReceivedQty(BigDecimal.ZERO);
        entity.setIssuedQty(BigDecimal.ZERO);
        entity.setStatus(status);
        entity.setSourceDocType(bo.getSourceDocType());
        entity.setSourceDocId(bo.getSourceDocId());
        entity.setSourceDocNo(bo.getSourceDocNo());
        entity.setRuleId(bo.getRuleId());
        entity.setAttr1(bo.getAttr1());
        entity.setAttr2(bo.getAttr2());
        entity.setAttr3(bo.getAttr3());
        entity.setAttr4(bo.getAttr4());
        entity.setRemark(bo.getRemark());
        entity.setDelFlag("0");
        return entity;
    }

    /** Lock before comparing echoed facts; a metadata update never writes a stale source/counter snapshot. */
    public int updateMetadata(WmBatchBo bo) {
        if (bo == null || bo.getBatchId() == null) throw new ServiceException("批次ID不能为空");
        String tenant = writeTenant();
        WmBatch current = batches.selectOne(Wrappers.<WmBatch>lambdaQuery()
            .eq(WmBatch::getTenantId, tenant).eq(WmBatch::getBatchId, bo.getBatchId()).last("FOR UPDATE"));
        if (current == null) throw new ServiceException("当前租户批次不存在");
        unchanged("批次编码", bo.getBatchCode(), current.getBatchCode());
        unchanged("物料ID", bo.getMaterialId(), current.getMaterialId());
        unchanged("物料编码", bo.getMaterialCode(), current.getMaterialCode());
        unchanged("物料名称", bo.getMaterialName(), current.getMaterialName());
        unchanged("物料规格", bo.getMaterialSpec(), current.getMaterialSpec());
        unchanged("供应商批号", bo.getVendorBatchCode(), current.getVendorBatchCode());
        unchanged("供应商ID", bo.getVendorId(), current.getVendorId());
        unchanged("供应商编码", bo.getVendorCode(), current.getVendorCode());
        unchanged("供应商名称", bo.getVendorName(), current.getVendorName());
        unchanged("来源仓库ID", bo.getWarehouseId(), current.getWarehouseId());
        unchanged("来源仓库编码", bo.getWarehouseCode(), current.getWarehouseCode());
        unchanged("来源仓库名称", bo.getWarehouseName(), current.getWarehouseName());
        unchanged("生产日期", bo.getProductionDate(), current.getProductionDate());
        unchanged("来源类型", bo.getSourceDocType(), current.getSourceDocType());
        unchanged("来源单据ID", bo.getSourceDocId(), current.getSourceDocId());
        unchanged("来源单据号", bo.getSourceDocNo(), current.getSourceDocNo());
        unchanged("生成规则", bo.getRuleId(), current.getRuleId());
        unchanged("登记数量", bo.getQuantity(), current.getQuantity());
        unchanged("已收数量", bo.getReceivedQty(), current.getReceivedQty());
        unchanged("已发数量", bo.getIssuedQty(), current.getIssuedQty());
        unchanged("扩展字段1", bo.getAttr1(), current.getAttr1());
        unchanged("扩展字段2", bo.getAttr2(), current.getAttr2());
        unchanged("扩展字段3", bo.getAttr3(), current.getAttr3());
        unchanged("扩展字段4", bo.getAttr4(), current.getAttr4());
        unchanged("删除标志", bo.getDelFlag(), current.getDelFlag());
        if (bo.getStatus() != null) requireStatus(bo.getStatus());
        if (bo.getExpiryDate() == null && bo.getStatus() == null && bo.getRemark() == null) return 1;
        int rows = batches.update(null, Wrappers.<WmBatch>lambdaUpdate()
            .eq(WmBatch::getTenantId, tenant).eq(WmBatch::getBatchId, current.getBatchId())
            .set(bo.getExpiryDate() != null, WmBatch::getExpiryDate, bo.getExpiryDate())
            .set(bo.getStatus() != null, WmBatch::getStatus, bo.getStatus())
            .set(bo.getRemark() != null, WmBatch::getRemark, bo.getRemark())
            .set(WmBatch::getUpdateTime, new Date()));
        if (rows != 1) throw new ServiceException("批次元数据更新失败");
        return rows;
    }

    /** The condition is evaluated under the UPDATE row lock; no previously read entity is written back. */
    public int expireDue() {
        String tenant = writeTenant();
        return batches.update(null, Wrappers.<WmBatch>lambdaUpdate()
            .eq(WmBatch::getTenantId, tenant).eq(WmBatch::getStatus, "ACTIVE")
            .isNotNull(WmBatch::getExpiryDate).lt(WmBatch::getExpiryDate, new Date())
            .set(WmBatch::getStatus, "EXPIRED").set(WmBatch::getUpdateTime, new Date()));
    }

    public void rejectQuantityEdit() {
        throw new ServiceException("批次累计数量不可直接修改，请通过收货、发料、退货等来源单据过账");
    }

    public void rejectDeletion() {
        throw new ServiceException("已登记批次不可删除或回收，请改为INACTIVE停用（兼容CLOSED）并保留来源及库存追溯事实");
    }

    private String writeTenant() {
        String tenant = context.tenant();
        context.requireWrite(tenant);
        return tenant;
    }

    private static void requireStatus(String status) {
        if (!METADATA_STATUSES.contains(status)) throw new ServiceException("批次元数据状态仅允许ACTIVE、EXPIRED、LOCKED、INACTIVE、CLOSED");
    }

    private static boolean nonzero(BigDecimal value) { return value != null && value.signum() != 0; }

    private static void unchanged(String name, Object requested, Object current) {
        boolean equal = requested instanceof BigDecimal number && current instanceof BigDecimal original
            ? number.compareTo(original) == 0 : Objects.equals(requested, current);
        if (requested != null && !equal) throw new ServiceException("批次" + name + "是已登记事实，不可直接修改，请通过来源单据处理");
    }
}
