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
 * 工作站对象 bd_workstation
 *
 * @author nexus
 * @date 2025-11-19
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.bd.mapper.BdWorkstationMapper.BdWorkstationResult")
public class BdWorkstation extends BaseEntity
        {
private static final long serialVersionUID = 1L;

    /** 工作站ID */
    @TableId(value = "workstation_id",type = IdType.AUTO)
    private Long workstationId;
    /** 工作站编码 */
    @Excel(name = "工作站编码")
    private String workstationCode;
    /** 工作站名称 */
    @Excel(name = "工作站名称")
    private String workstationName;
    /** 所属车间ID */
    @Excel(name = "所属车间ID")
    private Long workshopId;
    /** 车间编码 */
    @Excel(name = "车间编码")
    private String workshopCode;
    /** 车间名称 */
    @Excel(name = "车间名称")
    private String workshopName;
    /** 工作中心描述 */
    @Excel(name = "工作中心描述")
    private String workstationDesc;
    /** 设备ID */
    @Excel(name = "设备ID")
    private Long equipmentId;
    /** 标准操作工人数 */
    @Excel(name = "标准操作工人数")
    private Long operatorCount;
    /** 标准产能 */
    @Excel(name = "标准产能")
    private BigDecimal standardCapacity;
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