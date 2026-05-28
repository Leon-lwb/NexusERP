package com.nexus.erp.bd.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

/**
 * 工作站对象 bd_workstation
 *
 * @author nexus
 * @date 2025-11-19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("bd_workstation")
public class BdWorkstation extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 工作站ID
     */
    @TableId(value = "workstation_id")
    private Long workstationId;

    /**
     * 工作站编码
     */
    @ExcelProperty("工作站编码")
    private String workstationCode;

    /**
     * 工作站名称
     */
    @ExcelProperty("工作站名称")
    private String workstationName;

    /**
     * 所属车间ID
     */
    @ExcelProperty("所属车间ID")
    private Long workshopId;

    /**
     * 车间编码
     */
    @ExcelProperty("车间编码")
    private String workshopCode;

    /**
     * 车间名称
     */
    @ExcelProperty("车间名称")
    private String workshopName;

    /**
     * 工作中心描述
     */
    @ExcelProperty("工作中心描述")
    private String workstationDesc;

    /**
     * 设备ID
     */
    @ExcelProperty("设备ID")
    private Long equipmentId;

    /**
     * 标准操作工人数
     */
    @ExcelProperty("标准操作工人数")
    private Long operatorCount;

    /**
     * 标准产能
     */
    @ExcelProperty("标准产能")
    private BigDecimal standardCapacity;

    /**
     * 状态
     */
    @ExcelProperty("状态")
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
     * 删除标志
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;
}
