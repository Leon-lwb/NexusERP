package com.nexus.mes.qc.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.qc.domain.QcOqc;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * MES出货检验视图对象 qc_oqc
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = QcOqc.class)
public class QcOqcVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** OQC ID */
    @ExcelProperty("OQC ID")
    private Long oqcId;

    /** OQC编号 */
    @ExcelProperty("OQC编号")
    private String oqcNo;

    /** ERP销售订单ID */
    private Long soId;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    @ExcelProperty("物料编码")
    private String materialCode;

    /** 物料名称 */
    @ExcelProperty("物料名称")
    private String materialName;

    /** 检验数量 */
    @ExcelProperty("检验数量")
    private BigDecimal quantity;

    /** 抽样数量 */
    @ExcelProperty("抽样数量")
    private BigDecimal sampleQty;

    /** 检验结果 */
    @ExcelProperty("检验结果")
    private String inspectResult;

    /** 不良品信息(JSON) */
    private String defectInfo;

    /** 检验员ID */
    private Long inspectorId;

    /** 检验员姓名 */
    @ExcelProperty("检验员")
    private String inspectorName;

    /** 检验时间 */
    @ExcelProperty("检验时间")
    private Date inspectTime;

    /** 状态 */
    @ExcelProperty("状态")
    private String status;

    /** 备注 */
    private String remark;
}
