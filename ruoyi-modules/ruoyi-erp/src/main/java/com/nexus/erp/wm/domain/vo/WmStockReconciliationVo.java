package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.github.linpeilie.annotations.AutoMapper;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmStockReconciliation;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 库存台账校验视图对象
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmStockReconciliation.class)
public class WmStockReconciliationVo extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 校验ID
     */
    @ExcelProperty(value = "校验ID")
    private Long reconId;

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
     * 单位名称
     */
    @ExcelProperty(value = "单位名称")
    private String baseUomName;

    /**
     * 账面库存
     */
    @ExcelProperty(value = "账面库存")
    private BigDecimal bookQty;

    /**
     * 事务汇总数量
     */
    @ExcelProperty(value = "事务汇总")
    private BigDecimal txBalance;

    /**
     * 差异数量
     */
    @ExcelProperty(value = "差异数量")
    private BigDecimal diffQty;

    /**
     * 校验状态
     */
    @ExcelProperty(value = "校验状态")
    private String status;

    /**
     * 校验时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "校验时间")
    private Date reconTime;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;
}
