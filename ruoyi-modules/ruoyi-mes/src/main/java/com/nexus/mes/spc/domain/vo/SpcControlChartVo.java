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

    /** 样本大小 */
    @ExcelProperty("样本大小")
    private Integer sampleSize;

    /** 状态 */
    @ExcelProperty("状态")
    private String status;

    /** 备注 */
    private String remark;
}
