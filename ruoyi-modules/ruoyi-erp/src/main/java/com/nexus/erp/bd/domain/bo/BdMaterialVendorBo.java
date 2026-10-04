package com.nexus.erp.bd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.bd.domain.BdMaterialVendor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 物料-供应商关系业务对象 bd_material_vendor
 *
 * @author nexus
 * @date 2026-06-14
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BdMaterialVendor.class, reverseConvertGenerate = false)
public class BdMaterialVendorBo extends BaseEntity {

    /**
     * 关系ID
     */
    private Long relationId;

    /**
     * 物料ID
     */
    @NotNull(message = "物料ID不能为空")
    private Long materialId;

    /**
     * 供应商ID
     */
    @NotNull(message = "供应商ID不能为空")
    private Long vendorId;

    /**
     * 供应商编码
     */
    private String vendorCode;

    /**
     * 供应商名称
     */
    private String vendorName;

    /**
     * 供货周期(天)
     */
    private Integer leadTime;

    /**
     * 最小订货量
     */
    private BigDecimal minOrderQty;

    /**
     * 单价
     */
    private BigDecimal price;

    /**
     * 币种
     */
    private String currency;

    /**
     * 是否默认供应商 0:否 1:是
     */
    private Long isDefault;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 生效日期
     */
    private Date effectiveDate;

    /**
     * 失效日期
     */
    private Date expiryDate;

    /**
     * 状态 0:停用 1:启用
     */
    private Long status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;


    /** 明确登记的订购单位。 */
    private Long orderUomId;

    /** 明确登记的起订量单位。 */
    private Long moqUomId;

    /** 独立登记的起订量，不回填旧起订量。 */
    private BigDecimal verifiedMoqQty;

    /** 以订购单位计的订购倍数。 */
    private BigDecimal orderMultiple;

    /** 是否允许拆零；空值仍代表未知。 */
    private Boolean allowSplit;

    /** 每包装对应的物料基本单位数量。 */
    private BigDecimal unitsPerPack;

    /** 新条款生效时间。 */
    private Date termsEffectiveAt;

    /** 新条款到期时间。 */
    private Date termsExpiresAt;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}
