package com.nexus.core.bd.domain;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 车间设置对象 bd_workshop
 *
 * @author nexus
 * @date 2025-11-18
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.bd.mapper.BdWorkshopMapper.BdWorkshopResult")
public class BdWorkshop extends BaseEntity
        {
private static final long serialVersionUID = 1L;

    /** 车间ID */
    @TableId(value = "workshop_id",type = IdType.AUTO)
    private Long workshopId;
    /** 车间编码 */
    @Excel(name = "车间编码")
    private String workshopCode;
    /** 车间名称 */
    @Excel(name = "车间名称")
    private String workshopName;
    /** 车间描述 */
    @Excel(name = "车间描述")
    private String workshopDesc;
    /** 车间主任ID */
    private Long managerId;
    /** 车间主任 */
    @Excel(name = "车间主任")
    private String managerName;
    /** 产能（标准工时/天） */
    @Excel(name = "产能", readConverterExp = "标=准工时/天")
    private BigDecimal capacity;
    /** 状态 */
    @Excel(name = "状态")
    private Long status;
    /** 排序 */
    @Excel(name = "排序")
    private Long sortOrder;
    /** 预留字段1 */
    private String attr1;
    /** 预留字段2 */
    private String attr2;
    /** 预留字段3 */
    private Long attr3;
    /** 预留字段4 */
    private Long attr4;
}