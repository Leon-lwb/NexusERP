package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmBatch;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 批次管理业务对象 wm_batch
 *
 * @author nexus
 * @date 2026-06-02
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmBatch.class, reverseConvertGenerate = false)
public class WmBatchBo extends BaseEntity {

    /**
     * 批次ID
     */
    private Long batchId;

    /**
     * 批次编码
     */
    @NotBlank(message = "批次编码不能为空")
    private String batchCode;

    /**
     * 物料ID
     */
    @NotNull(message = "物料ID不能为空")
    private Long materialId;

    /**
     * 物料编码
     */
    private String materialCode;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 规格型号
     */
    private String materialSpec;

    /**
     * 供应商ID
     */
    private Long vendorId;

    /**
     * 供应商编码
     */
    private String vendorCode;

    /**
     * 供应商名称
     */
    private String vendorName;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 仓库编码
     */
    private String warehouseCode;

    /**
     * 仓库名称
     */
    private String warehouseName;

    /**
     * 生产日期
     */
    private Date productionDate;

    /**
     * 有效期
     */
    private Date expiryDate;

    /**
     * 批次数量
     */
    private BigDecimal quantity;

    /**
     * 已收数量
     */
    private BigDecimal receivedQty;

    /**
     * 已发数量
     */
    private BigDecimal issuedQty;

    /**
     * 状态
     */
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
    private String delFlag;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}
