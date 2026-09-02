package com.nexus.mes.qc.domain.vo;

import com.nexus.mes.qc.domain.QcInspectItem;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * QC检验项目 视图对象
 */
@Data
@AutoMapper(target = QcInspectItem.class)
public class QcInspectItemVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long itemId;
    /** 关联模板ID */
    private Long templateId;
    /** 项目编码 */
    private String itemCode;
    /** 项目名称 */
    private String itemName;
    /** 数据类型: QUANTITATIVE/QUALITATIVE */
    private String dataType;
    /** 计量单位 */
    private String unit;
    /** 标准类型: NOMINAL/LSL_ONLY/USL_ONLY/LSL_USL */
    private String standardType;
    /** 名义值 */
    private BigDecimal nominalValue;
    /** 下规格限LSL */
    private BigDecimal lslValue;
    /** 上规格限USL */
    private BigDecimal uslValue;
    /** 上公差 */
    private BigDecimal tolUpper;
    /** 下公差 */
    private BigDecimal tolLower;
    /** 检验水平 */
    private String aqlLevel;
    /** 抽样方案 */
    private String samplePlanCode;
    /** 关键度: CRITICAL/MAJOR/MINOR */
    private String criticality;
    /** 序号 */
    private Integer seqNum;
    /** 是否启用 */
    private String enabled;
    /** 备注 */
    private String remark;
}
