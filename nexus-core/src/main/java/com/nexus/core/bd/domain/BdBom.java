package com.nexus.core.bd.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 产品BOM对象 bd_bom
 *
 * @author nexus
 * @date 2025-10-18
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.bd.mapper.BdBomMapper.BdBomResult")
public class BdBom extends BaseEntity
        {
private static final long serialVersionUID = 1L;

    /** BOM主表ID */
    @TableId(value = "bom_id",type = IdType.AUTO)
    private Long bomId;
    /** 父项物料ID */
    private Long parentMaterialId;
    /** BOM版本号 */
    @Excel(name = "BOM版本号")
    private String bomVersion;
    /** BOM名称 */
    @Excel(name = "BOM名称")
    private String bomName;
    /** 生效日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "生效日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date effectiveDate;
    /** 失效日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "失效日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date expiryDate;
    /** 是否当前版本 */
    @Excel(name = "是否当前版本")
    private Long isCurrent;
    /** 状态 */
    @Excel(name = "状态")
    private Long status;
    /** 预留字段1 */
    private String attr1;
    /** 预留字段2 */
    private String attr2;
    /** 预留字段3 */
    private Long attr3;
    /** 预留字段4 */
    private Long attr4;
}