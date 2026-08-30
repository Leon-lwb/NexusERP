package com.nexus.mes.qc.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.qc.domain.QcIqc;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * MES来料检验视图对象 qc_iqc
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = QcIqc.class)
public class QcIqcVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** IQC ID */
    @ExcelProperty("IQC ID")
    private Long iqcId;

    /** IQC编号 */
    @ExcelProperty("IQC编号")
    private String iqcNo;

    /** ERP收货单ID */
    private Long receiveId;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    @ExcelProperty("物料编码")
    private String materialCode;

    /** 物料名称 */
    @ExcelProperty("物料名称")
    private String materialName;

    /** 供应商ID */
    private Long vendorId;

    /** 供应商名称 */
    @ExcelProperty("供应商名称")
    private String vendorName;

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
    /** 检验照片附件ossId串(逗号分隔) */
    private String photoOssIds;

    private String remark;
}
