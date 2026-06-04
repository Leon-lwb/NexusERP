package com.nexus.erp.wm.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 批次管理对象 wm_batch
 *
 * @author nexus
 * @date 2026-06-02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("wm_batch")
public class WmBatch extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 批次ID
     */
    @TableId(value = "batch_id")
    private Long batchId;

    /**
     * 批次编码
     */
    @ExcelProperty("批次编码")
    private String batchCode;

    /**
     * 物料ID
     */
    @ExcelProperty("物料ID")
    private Long materialId;

    /**
     * 物料编码
     */
    @ExcelProperty("物料编码")
    private String materialCode;

    /**
     * 物料名称
     */
    @ExcelProperty("物料名称")
    private String materialName;

    /**
     * 规格型号
     */
    @ExcelProperty("规格型号")
    private String materialSpec;

    /**
     * 供应商原始批次号（记录供应商提供的批次号，便于追溯）
     */
    @ExcelProperty("供应商原始批次号")
    private String vendorBatchCode;

    /**
     * 供应商ID
     */
    private Long vendorId;

    /**
     * 供应商编码
     */
    @ExcelProperty("供应商编码")
    private String vendorCode;

    /**
     * 供应商名称
     */
    @ExcelProperty("供应商名称")
    private String vendorName;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 仓库编码
     */
    @ExcelProperty("仓库编码")
    private String warehouseCode;

    /**
     * 仓库名称
     */
    @ExcelProperty("仓库名称")
    private String warehouseName;

    /**
     * 生产日期
     */
    @ExcelProperty("生产日期")
    private Date productionDate;

    /**
     * 有效期
     */
    @ExcelProperty("有效期")
    private Date expiryDate;

    /**
     * 批次数量
     */
    @ExcelProperty("批次数量")
    private BigDecimal quantity;

    /**
     * 已收数量
     */
    @ExcelProperty("已收数量")
    private BigDecimal receivedQty;

    /**
     * 已发数量
     */
    @ExcelProperty("已发数量")
    private BigDecimal issuedQty;

    /**
     * 状态
     */
    @ExcelProperty("状态")
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
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}
