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
 * 库存台账校验对象 wm_stock_reconciliation
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("wm_stock_reconciliation")
public class WmStockReconciliation extends TenantEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "recon_id")
    private Long reconId;

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

    @ExcelProperty("账面库存")
    private BigDecimal bookQty;

    @ExcelProperty("事务汇总")
    private BigDecimal txBalance;

    @ExcelProperty("差异数量")
    private BigDecimal diffQty;

    @ExcelProperty("校验状态")
    private String status;

    @ExcelProperty("校验时间")
    private Date reconTime;

    @TableLogic
    private String delFlag;

    @ExcelProperty("备注")
    private String remark;
}
