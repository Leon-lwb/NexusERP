package com.nexus.erp.bd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.bd.domain.BdWorkstation;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工作站视图对象 bd_workstation
 *
 * @author nexus
 * @date 2025-11-19
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BdWorkstation.class)
public class BdWorkstationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 工作站ID
     */
    @ExcelProperty(value = "工作站ID")
    private Long workstationId;

    /**
     * 工作站编码
     */
    @ExcelProperty(value = "工作站编码")
    private String workstationCode;

    /**
     * 工作站名称
     */
    @ExcelProperty(value = "工作站名称")
    private String workstationName;

    /**
     * 所属车间ID
     */
    @ExcelProperty(value = "所属车间ID")
    private Long workshopId;

    /**
     * 车间编码
     */
    @ExcelProperty(value = "车间编码")
    private String workshopCode;

    /**
     * 车间名称
     */
    @ExcelProperty(value = "车间名称")
    private String workshopName;

    /**
     * 工作中心描述
     */
    @ExcelProperty(value = "工作中心描述")
    private String workstationDesc;

    /**
     * 设备ID
     */
    @ExcelProperty(value = "设备ID")
    private Long equipmentId;

    /**
     * 标准操作工人数
     */
    @ExcelProperty(value = "标准操作工人数")
    private Long operatorCount;

    /**
     * 标准产能
     */
    @ExcelProperty(value = "标准产能")
    private BigDecimal standardCapacity;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private Long status;

    /**
     * 备注
     */
    private String remark;

}
