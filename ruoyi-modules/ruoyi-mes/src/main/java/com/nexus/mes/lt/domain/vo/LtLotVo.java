package com.nexus.mes.lt.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.lt.domain.LtLot;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * MES批次视图对象 lt_lot
 * <p>属于 MES 批次管理模块，作为 批次主档 的查询/导出返回结构。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = LtLot.class)
public class LtLotVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 批次ID */
    @ExcelProperty("批次ID")
    private Long lotId;

    /** 批次编码 */
    @ExcelProperty("批次编码")
    private String lotCode;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    @ExcelProperty("物料编码")
    private String materialCode;

    /** 物料名称 */
    @ExcelProperty("物料名称")
    private String materialName;

    /** 供应商ID */
    private Long supplierId;

    /** 供应商名称 */
    @ExcelProperty("供应商名称")
    private String supplierName;

    /** 供应商批次号 */
    @ExcelProperty("供应商批次号")
    private String supplierLot;

    /** 批次数量 */
    @ExcelProperty("批次数量")
    private BigDecimal quantity;

    /** 已消耗数量 */
    @ExcelProperty("已消耗数量")
    private BigDecimal consumedQty;

    /** 剩余数量(非DB,quantity - consumed_qty) */
    @ExcelProperty("剩余数量")
    private BigDecimal remainingQty;

    /** 生产日期 */
    @ExcelProperty("生产日期")
    private Date producedDate;

    /** 有效期 */
    @ExcelProperty("有效期")
    private Date expiryDate;

    /** 状态 */
    @ExcelProperty("状态")
    private String status;

    /** QC状态 */
    @ExcelProperty("QC状态")
    private String qcStatus;

    /** IQC检验单ID(关联qc_iqc, 可空; 关联后IQC完成自动回写) */
    private Long iqcId;

    /** 备注 */
    private String remark;

    // ==================== 追溯辅助字段(非DB) ====================

    /** 工单消耗列表(追溯用) */
    private List<LtWoConsumptionVo> consumptions;

    /** SN绑定列表(追溯用) */
    private List<LtSnBindingVo> bindings;
}
