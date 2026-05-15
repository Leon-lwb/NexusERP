package org.dromara.erp.bd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.erp.bd.domain.BdBom;

import java.util.Date;

/**
 * 产品BOM业务对象 bd_bom
 *
 * @author ruoyi
 * @date 2025-10-18
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BdBom.class, reverseConvertGenerate = false)
public class BdBomBo extends BaseEntity {

    /**
     * BOM主表ID
     */
    private Long bomId;

    /**
     * 父项物料ID
     */
    private Long parentMaterialId;

    /**
     * BOM版本号
     */
    @NotBlank(message = "BOM版本号不能为空")
    private String bomVersion;

    /**
     * BOM名称
     */
    @NotBlank(message = "BOM名称不能为空")
    private String bomName;

    /**
     * 生效日期
     */
    private Date effectiveDate;

    /**
     * 失效日期
     */
    private Date expiryDate;

    /**
     * 是否当前版本
     */
    private Long isCurrent;

    /**
     * 状态
     */
    private Long status;

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

    /**
     * 来源类型（PROCUREMENT-采购, PROCESS-加工）
     */
    private String sourceType;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;

}
