package com.nexus.erp.bd.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 物料-供应商关系对象 bd_material_vendor
 *
 * @author nexus
 * @date 2026-06-14
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("bd_material_vendor")
public class BdMaterialVendor extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 关系ID
     */
    @TableId(value = "relation_id")
    private Long relationId;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 供应商ID
     */
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

    /** 经业务核验的订购单位；历史供应商条款不得推断。 */
    @TableField("order_uom_id")
    private Long orderUomId;

    /** 经业务核验的 MOQ，不从 minOrderQty 自动回填。 */
    @TableField("verified_moq_qty")
    private BigDecimal verifiedMoqQty;

    @TableField("moq_uom_id")
    private Long moqUomId;

    @TableField("order_multiple")
    private BigDecimal orderMultiple;

    /** NULL 代表未核验，不能当作允许拆零。 */
    @TableField("allow_split")
    private Boolean allowSplit;

    @TableField("units_per_pack")
    private BigDecimal unitsPerPack;

    @TableField("terms_version")
    private String termsVersion;

    @TableField("terms_effective_at")
    private Date termsEffectiveAt;

    @TableField("terms_expires_at")
    private Date termsExpiresAt;

    @TableField("terms_verified_at")
    private Date termsVerifiedAt;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}
