package com.nexus.erp.bd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.bd.domain.BdWorkshop;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 车间设置视图对象 bd_workshop
 *
 * @author nexus
 * @date 2025-11-18
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BdWorkshop.class)
public class BdWorkshopVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 车间ID
     */
    @ExcelProperty(value = "车间ID")
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
     * 车间描述
     */
    @ExcelProperty(value = "车间描述")
    private String workshopDesc;

    /**
     * 车间主任ID
     */
    private Long managerId;

    /**
     * 车间主任
     */
    @ExcelProperty(value = "车间主任")
    private String managerName;

    /**
     * 所属部门ID（关联sys_dept，打通行政组织体系）
     */
    private Long deptId;

    /**
     * 所属部门名称
     */
    @ExcelProperty(value = "所属部门")
    private String deptName;

    /**
     * 产能（标准工时/天）
     */
    @ExcelProperty(value = "产能")
    private BigDecimal capacity;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private Long status;

    /**
     * 排序
     */
    @ExcelProperty(value = "排序")
    private Long sortOrder;

}
