package com.nexus.mes.spc.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.spc.domain.SpcControlChart;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * MES SPC控制图视图对象 spc_control_chart
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SpcControlChart.class)
public class SpcControlChartVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 控制图ID */
    @ExcelProperty("控制图ID")
    private Long chartId;

    /** 控制图名称 */
    @ExcelProperty("控制图名称")
    private String chartName;

    /** 控制图类型 */
    @ExcelProperty("控制图类型")
    private String chartType;

    /** 关联采集点ID */
    private Long pointId;

    /** 关联物料ID */
    private Long materialId;

    /** 关联工序ID */
    private Long processId;

    /** 上控制限 */
    @ExcelProperty("UCL")
    private BigDecimal ucl;

    /** 中心线 */
    @ExcelProperty("CL")
    private BigDecimal cl;

    /** 下控制限 */
    @ExcelProperty("LCL")
    private BigDecimal lcl;

    /** 次级图(极差图R/标准差图S/移动极差图MR)上控制限 */
    @ExcelProperty("次级图UCL")
    private BigDecimal rUcl;

    /** 次级图中心线(R̄/S̄/MR̄) */
    @ExcelProperty("次级图CL")
    private BigDecimal rCl;

    /** 次级图下控制限 */
    @ExcelProperty("次级图LCL")
    private BigDecimal rLcl;

    /** 规格上限(用户维护) */
    @ExcelProperty("USL")
    private BigDecimal usl;

    /** 规格下限(用户维护) */
    @ExcelProperty("LSL")
    private BigDecimal lsl;

    /** 过程能力指数Cp */
    @ExcelProperty("Cp")
    private BigDecimal cp;

    /** 过程能力指数Cpk */
    @ExcelProperty("Cpk")
    private BigDecimal cpk;

    /** 能力等级: A+/A/B/C */
    @ExcelProperty("能力等级")
    private String cpkGrade;

    /** 过程性能指数Pp(长期标准差) */
    @ExcelProperty("Pp")
    private BigDecimal pp;

    /** 过程性能指数Ppk(长期标准差) */
    @ExcelProperty("Ppk")
    private BigDecimal ppk;

    /** 过程能力样本量不足标记(仅 analyze 返回, 不落库): 子组数<25 或总样本数<50 或未维护规格限 */
    private Boolean capabilityInsufficient;

    /** 过程能力不足原因文案(仅 analyze 返回, 不落库) */
    private String capabilityInsufficientReason;

    /** 样本大小 */
    @ExcelProperty("样本大小")
    private Integer sampleSize;

    /** 状态 */
    @ExcelProperty("状态")
    private String status;

    /** 备注 */
    private String remark;
}
