package com.nexus.mes.qc.domain.vo;

import com.nexus.mes.qc.domain.QcInspectDetail;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * QC 检验单明细 视图对象
 * <p>检验单详情接口随 header 一起返回, 供前端"检验项目"标签页录入与展示。</p>
 */
@Data
@AutoMapper(target = QcInspectDetail.class)
public class QcInspectDetailVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long detailId;
    /** 检验单ID */
    private Long checkId;
    /** 检验类别: IQC/IPQC/OQC */
    private String checkType;
    /** 来源模板ID */
    private Long templateId;
    /** 来源检验项目ID */
    private Long itemId;
    /** 项目编码 */
    private String itemCode;
    /** 项目名称 */
    private String itemName;
    /** 数据类型: QUANTITATIVE/QUALITATIVE */
    private String dataType;
    /** 标准类型: NOMINAL/LSL_ONLY/USL_ONLY/LSL_USL */
    private String standardType;
    /** 标准描述(如 10±0.05mm) */
    private String standardDesc;
    /** 单位 */
    private String unit;
    /** 关键度: CRITICAL/MAJOR/MINOR */
    private String criticality;
    /** 样本量 */
    private BigDecimal sampleQty;
    /** 实测值 */
    private String measuredValue;
    /** 名义值(判定用) */
    private BigDecimal nominalValue;
    /** 下限(判定用) */
    private BigDecimal lslValue;
    /** 上限(判定用) */
    private BigDecimal uslValue;
    /** 上公差(判定用) */
    private BigDecimal tolUpper;
    /** 下公差(判定用) */
    private BigDecimal tolLower;
    /** 判定结果: PASS/FAIL/PENDING */
    private String judgeResult;
    /** 序号 */
    private Integer seqNum;
    /** 备注 */
    private String remark;
}
