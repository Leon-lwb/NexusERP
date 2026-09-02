package com.nexus.mes.spc.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.spc.domain.SpcControlChart;

/**
 * MES SPC控制图业务对象 spc_control_chart
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SpcControlChart.class, reverseConvertGenerate = false)
public class SpcControlChartBo extends BaseEntity {

    /** 控制图ID */
    private Long chartId;

    /** 控制图名称 */
    @NotBlank(message = "控制图名称不能为空")
    private String chartName;

    /** 控制图类型: X_BAR_R/X_BAR_S/P/C/U */
    @NotBlank(message = "控制图类型不能为空")
    private String chartType;

    /** 关联采集点ID */
    private Long pointId;

    /** 关联物料ID */
    private Long materialId;

    /** 关联工序ID */
    private Long processId;

    /** 上控制限 */
    private java.math.BigDecimal ucl;

    /** 中心线 */
    private java.math.BigDecimal cl;

    /** 下控制限 */
    private java.math.BigDecimal lcl;

    /** 规格上限(计量型图适用, 用户维护) */
    private java.math.BigDecimal usl;

    /** 规格下限 */
    private java.math.BigDecimal lsl;

    /** 样本大小 */
    private Integer sampleSize;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}
