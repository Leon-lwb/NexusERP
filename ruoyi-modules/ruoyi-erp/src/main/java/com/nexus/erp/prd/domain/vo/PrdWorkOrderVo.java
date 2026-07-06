package com.nexus.erp.prd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.prd.domain.PrdWorkOrder;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 生产工单视图对象 prd_work_order
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PrdWorkOrder.class)
public class PrdWorkOrderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 工单ID
     */
    @ExcelProperty("工单ID")
    private Long woId;

    /**
     * 工单编号
     */
    @ExcelProperty("工单编号")
    private String woNo;

    /**
     * 计划名称
     */
    @ExcelProperty("计划名称")
    private String planName;

    /**
     * 来源类型
     */
    private String sourceType;

    /**
     * 来源单据ID
     */
    private Long sourceId;

    /**
     * 销售订单编号
     */
    private String sourceNo;

    /**
     * 来源单据行ID
     */
    private Long sourceLineId;

    /**
     * 产品物料ID
     */
    private Long materialId;

    /**
     * 产品编码
     */
    private String materialCode;

    /**
     * 产品名称
     */
    private String materialName;

    /**
     * 产品规格
     */
    private String materialSpec;

    /**
     * 计划数量
     */
    @ExcelProperty("计划数量")
    private BigDecimal plannedQty;

    /**
     * 已完成数量
     */
    @ExcelProperty("已完成数量")
    private BigDecimal completedQty;

    /**
     * 报废数量
     */
    @ExcelProperty("报废数量")
    private BigDecimal scrapQty;

    /**
     * 单位id
     */
    private Long unitId;

    /**
     * 单位名称
     */
    private String unitName;

    /**
     * 计划开始日期
     */
    private Date planStartDate;

    /**
     * 计划结束日期
     */
    private Date planEndDate;

    /**
     * 实际开始时间
     */
    private Date actualStartDate;

    /**
     * 实际结束时间
     */
    private Date actualEndDate;

    /**
     * 工单状态
     */
    @ExcelProperty("工单状态")
    private String woStatus;

    /**
     * 车间ID
     */
    private Long workshopId;

    /**
     * 车间名称
     */
    private String workshopName;

    /**
     * 工作站ID
     */
    private Long workstationId;

    /**
     * 工作站名称
     */
    private String workstationName;

    /**
     * 生产线ID
     */
    private Long productionLineId;

    /**
     * 负责人ID
     */
    private Long responsibleId;

    /**
     * 负责人
     */
    private String responsibleName;

    /**
     * 班组ID
     */
    private Long teamId;

    /**
     * 班组
     */
    private String teamName;

    /**
     * 优先级
     */
    private Long priority;

    /**
     * 来源计划ID
     */
    private Long planId;

    /**
     * BOMid
     */
    private Long bomId;

    /**
     * BOM版本
     */
    private String bomVersion;

    /**
     * BOM名称
     */
    @ExcelProperty("BOM名称")
    private String bomName;

    /**
     * attr2
     */
    private String attr2;

    /**
     * attr3
     */
    private Long attr3;

    /**
     * attr4
     */
    private Long attr4;

    /**
     * 备注
     */
    private String remark;

    /**
     * 已选审批流程模板ID
     */
    private Long selectFlowId;

    /**
     * 是否倒冲工单: 0-否, 1-是
     */
    @ExcelProperty("是否倒冲")
    private Integer backflushFlag;

    /**
     * 累计发料数量
     */
    @ExcelProperty("累计发料数量")
    private BigDecimal issuedQty;

    /**
     * 生产部门ID（非持久化，通过 workshop → bd_workshop.dept_id JOIN 反查填入）
     */
    private Long deptId;

    /**
     * 生产部门名称（非持久化，同上）
     */
    private String deptName;
}
