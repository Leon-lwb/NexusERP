package com.nexus.erp.bd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * BOM对象 bd_bom
 *
 * @author wanghuan
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("bd_bom")
public class BdBom extends TenantEntity {

    /**
     * BOM ID
     */
    @TableId(value = "bom_id")
    private Long bomId;

    /**
     * 父项物料ID
     */
    private Long parentMaterialId;

    /**
     * BOM版本
     */
    private String bomVersion;

    /**
     * BOM名称
     */
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
     * 是否当前版本（1-是，0-否）
     */
    private Long isCurrent;

    /**
     * 状态
     */
    private Long status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 来源类型（PROCUREMENT-采购, PROCESS-加工）
     */
    private String sourceType;

    /**
     * BOM类型: EBOM-工程BOM, MBOM-制造BOM, SBOM-服务BOM, CBOM-配置BOM
     */
    private String bomType;

    /**
     * 关联工艺路线ID
     */
    private Long routingId;

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

    @TableLogic
    private String delFlag;
}
