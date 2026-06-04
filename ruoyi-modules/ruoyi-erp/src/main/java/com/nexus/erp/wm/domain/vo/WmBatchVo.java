package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmBatch;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 批次管理视图对象 wm_batch
 *
 * @author nexus
 * @date 2026-06-02
 */
@Data
@ExcelIgnoreUnannotated
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmBatch.class)
public class WmBatchVo extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 批次ID
     */
    @ExcelProperty(value = "批次ID")
    private Long batchId;

    /**
     * 批次编码
     */
    @ExcelProperty(value = "批次编码")
    private String batchCode;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料编码
     */
    @ExcelProperty(value = "物料编码")
    private String materialCode;

    /**
     * 物料名称
     */
    @ExcelProperty(value = "物料名称")
    private String materialName;

    /**
     * 规格型号
     */
    @ExcelProperty(value = "规格型号")
    private String materialSpec;

    /**
     * 供应商原始批次号
     */
    @ExcelProperty(value = "供应商原始批次号")
    private String vendorBatchCode;

    /**
     * 供应商ID
     */
    private Long vendorId;

    /**
     * 供应商编码
     */
    @ExcelProperty(value = "供应商编码")
    private String vendorCode;

    /**
     * 供应商名称
     */
    @ExcelProperty(value = "供应商名称")
    private String vendorName;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 仓库编码
     */
    @ExcelProperty(value = "仓库编码")
    private String warehouseCode;

    /**
     * 仓库名称
     */
    @ExcelProperty(value = "仓库名称")
    private String warehouseName;

    /**
     * 生产日期
     */
    @ExcelProperty(value = "生产日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date productionDate;

    /**
     * 有效期
     */
    @ExcelProperty(value = "有效期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date expiryDate;

    /**
     * 批次数量
     */
    @ExcelProperty(value = "批次数量")
    private BigDecimal quantity;

    /**
     * 已收数量
     */
    @ExcelProperty(value = "已收数量")
    private BigDecimal receivedQty;

    /**
     * 已发数量
     */
    @ExcelProperty(value = "已发数量")
    private BigDecimal issuedQty;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private String status;

    /**
     * 来源单据类型
     */
    private String sourceDocType;

    /**
     * 来源单据ID
     */
    private Long sourceDocId;

    /**
     * 来源单据编号
     */
    private String sourceDocNo;

    /**
     * 生成规则ID
     */
    private Long ruleId;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;
}
