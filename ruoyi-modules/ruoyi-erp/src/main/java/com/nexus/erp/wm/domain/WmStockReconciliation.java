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

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 校验ID
     */
    @TableId(value = "recon_id")
    private Long reconId;

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
     * 单位名称
     */
    @ExcelProperty("单位名称")
    private String baseUomName;

    /**
     * 账面库存
     */
    @ExcelProperty("账面库存")
    private BigDecimal bookQty;

    /**
     * 事务汇总数量
     */
    @ExcelProperty("事务汇总")
    private BigDecimal txBalance;

    /**
     * 差异数量
     */
    @ExcelProperty("差异数量")
    private BigDecimal diffQty;

    /**
     * 校验状态
     */
    @ExcelProperty("校验状态")
    private String status;

    /**
     * 校验时间
     */
    @ExcelProperty("校验时间")
    private Date reconTime;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    @ExcelProperty("备注")
    private String remark;
}
