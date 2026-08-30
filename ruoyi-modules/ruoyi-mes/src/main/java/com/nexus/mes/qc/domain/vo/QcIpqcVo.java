package com.nexus.mes.qc.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.qc.domain.QcIpqc;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * MES过程检验视图对象 qc_ipqc
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = QcIpqc.class)
public class QcIpqcVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** IPQC ID */
    @ExcelProperty("IPQC ID")
    private Long ipqcId;

    /** IPQC编号 */
    @ExcelProperty("IPQC编号")
    private String ipqcNo;

    /** MES工单ID */
    private Long mesWoId;

    /** 工单编号 */
    @ExcelProperty("工单编号")
    private String woNo;

    /** 流转卡ID */
    private Long cardId;

    /** 流转卡工序记录ID */
    private Long stepRecordId;

    /** 工序ID */
    private Long processId;

    /** 工序编码 */
    @ExcelProperty("工序编码")
    private String processCode;

    /** 工序名称 */
    @ExcelProperty("工序名称")
    private String processName;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    @ExcelProperty("物料编码")
    private String materialCode;

    /** 物料名称 */
    @ExcelProperty("物料名称")
    private String materialName;

    /** 抽样数量 */
    @ExcelProperty("抽样数量")
    private BigDecimal sampleQty;

    /** 不良数量 */
    @ExcelProperty("不良数量")
    private BigDecimal defectQty;

    /** 不良率(%) */
    @ExcelProperty("不良率(%)")
    private BigDecimal defectRate;

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
