package com.nexus.erp.bd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.bd.domain.BdMaterialVendor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 物料-供应商关系视图对象 bd_material_vendor
 *
 * @author nexus
 * @date 2026-06-14
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BdMaterialVendor.class)
public class BdMaterialVendorVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关系ID
     */
    @ExcelProperty(value = "关系ID")
    private Long relationId;

    /**
     * 物料ID
     */
    @ExcelProperty(value = "物料ID")
    private Long materialId;

    /**
     * 物料编码（关联 bd_material 查询填充，非持久化字段）
     */
    @ExcelProperty(value = "物料编码")
    private String materialCode;

    /**
     * 物料名称（关联 bd_material 查询填充，非持久化字段）
     */
    @ExcelProperty(value = "物料名称")
    private String materialName;

    /**
     * 供应商ID
     */
    @ExcelProperty(value = "供应商ID")
    private Long vendorId;

    /**
     * 供应商编码
     */
    @ExcelProperty(value = "供应商编码")
    private String vendorCode;

    /**
     * 供应商名称
     */
    @ExcelProperty(value = "供应商名称")
    private String vendorName;

    /**
     * 供货周期(天)
     */
    @ExcelProperty(value = "供货周期(天)")
    private Integer leadTime;

    /**
     * 最小订货量
     */
    @ExcelProperty(value = "最小订货量")
    private BigDecimal minOrderQty;

    /**
     * 单价
     */
    @ExcelProperty(value = "单价")
    private BigDecimal price;

    /**
     * 币种
     */
    @ExcelProperty(value = "币种")
    private String currency;

    /**
     * 是否默认供应商 0:否 1:是
     */
    @ExcelProperty(value = "是否默认供应商")
    private Long isDefault;

    /**
     * 优先级
     */
    @ExcelProperty(value = "优先级")
    private Integer priority;

    /**
     * 生效日期
     */
    @ExcelProperty(value = "生效日期")
    private Date effectiveDate;

    /**
     * 失效日期
     */
    @ExcelProperty(value = "失效日期")
    private Date expiryDate;

    /**
     * 状态 0:停用 1:启用
     */
    @ExcelProperty(value = "状态")
    private Long status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
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

    /** 服务端生成的条款确认版本。 */
    private String termsVersion;

    /** 服务端生成的条款核验时间。 */
    private Date termsVerifiedAt;
}
