package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.github.linpeilie.annotations.AutoMapper;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmSn;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 序列号管理视图对象 wm_sn
 *
 * @author nexus
 * @date 2026-06-02
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmSn.class)
@EqualsAndHashCode(callSuper = true)
public class WmSnVo extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 序列号ID
     */
    @ExcelProperty(value = "序列号ID")
    private Long snId;

    /**
     * 序列号编码
     */
    @ExcelProperty(value = "序列号编码")
    private String snCode;

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
     * 关联批次ID
     */
    private Long batchId;

    /**
     * 关联批次编码
     */
    @ExcelProperty(value = "关联批次编码")
    private String batchCode;

    /**
     * 当前仓库ID
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
     * 当前库位ID
     */
    private Long locationId;

    /**
     * 库位编码
     */
    @ExcelProperty(value = "库位编码")
    private String locationCode;

    /**
     * 关联库存记录ID
     */
    private Long stockId;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private String status;

    /**
     * 来源单据类型
     */
    @ExcelProperty(value = "来源单据类型")
    private String sourceDocType;

    /**
     * 来源单据ID
     */
    private Long sourceDocId;

    /**
     * 来源单据编号
     */
    @ExcelProperty(value = "来源单据编号")
    private String sourceDocNo;

    /**
     * 生成规则ID
     */
    private Long ruleId;

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
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;
}
