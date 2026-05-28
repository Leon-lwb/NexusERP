package com.nexus.erp.prd.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

/**
 * 返工订单对象 prd_rework_order
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("prd_rework_order")
public class PrdReworkOrder extends TenantEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "rework_id")
    private Long reworkId;

    @ExcelProperty("返工单号")
    private String reworkNo;

    @ExcelProperty("来源工单ID")
    private Long sourceWoId;

    @ExcelProperty("来源工单编号")
    private String sourceWoNo;

    @ExcelProperty("物料ID")
    private Long materialId;

    @ExcelProperty("物料编码")
    private String materialCode;

    @ExcelProperty("物料名称")
    private String materialName;

    @ExcelProperty("规格型号")
    private String materialSpec;

    @ExcelProperty("单位名称")
    private String baseUomName;

    @ExcelProperty("返工数量")
    private BigDecimal reworkQty;

    @ExcelProperty("返工原因")
    private String reworkReason;

    @ExcelProperty("实际数量")
    private BigDecimal actualQty;

    @ExcelProperty("报废数量")
    private BigDecimal scrapQty;

    @ExcelProperty("返工状态")
    private String status;

    @TableLogic
    private String delFlag;

    @ExcelProperty("备注")
    private String remark;
}
