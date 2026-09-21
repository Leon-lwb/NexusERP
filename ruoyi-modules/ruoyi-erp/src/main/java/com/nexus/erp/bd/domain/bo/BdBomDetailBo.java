package com.nexus.erp.bd.domain.bo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import com.nexus.erp.bd.domain.BdBomDetail;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
/**
 * BOM明细业务对象（BO）
 * <p>用于 BOM 子项物料行的增改入参与校验，对应表 {@code bd_bom_detail}，字段含义与实体 {@link com.nexus.erp.bd.domain.BdBomDetail} 一致。</p>
 */
@AutoMapper(target = BdBomDetail.class, reverseConvertGenerate = false)
public class BdBomDetailBo extends BaseEntity {

    /** BOM明细ID */
    private Long detailId;

    /** BOM主表ID（必填） */
    @NotNull(message = "BOM主表ID不能为空")
    private Long bomId;

    /** 子项物料ID（必填） */
    @NotNull(message = "子项物料ID不能为空")
    private Long childMaterialId;

    /** 子项物料编码 */
    private String childMaterialCode;

    /** 子项物料名称 */
    private String childMaterialName;

    /** 单位父项用量（必填，须大于 0） */
    @NotNull(message = "单位父项用量不能为空")
    @DecimalMin(value = "0.000001", message = "用量必须大于0")
    @Digits(integer = 12, fraction = 6, message = "用量格式不正确")
    private BigDecimal quantityPerParent;

    /** 损耗率（%，0~100） */
    @DecimalMin(value = "0", message = "损耗率不能小于0")
    @DecimalMax(value = "100", message = "损耗率不能大于100")
    private BigDecimal lossRate;

    /** 工序步骤序号（必填，用于排序与工艺路线步骤关联） */
    @NotNull(message = "工序步骤不能为空")
    private Integer operationStep;

    /** 提前期（小时） */
    private Integer leadTime;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 失效日期 */
    private LocalDate expiryDate;

    /** 显示排序 */
    private Integer sortOrder;

    /** 状态（0-停用，1-启用） */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 来源类型：PROCUREMENT-采购，PROCESS-加工 */
    private String sourceType;

    /**
     * 供应方：SELF_PROVIDED-自备，VENDOR_PROVIDED-外协厂供，CUSTOMER_PROVIDED-客供。
     * <p>ERP-BD-01：该字段此前只存在于实体，BO/VO 都没有，导致委外快照恒为 SELF_PROVIDED。</p>
     */
    @Size(max = 32, message = "供应方长度不能超过32")
    private String supplyType;

    /** 预留字段1 */
    private String attr1;
    /** 预留字段2 */
    private String attr2;
    /** 预留字段3 */
    private Integer attr3;
    /** 预留字段4 */
    private Integer attr4;
}
