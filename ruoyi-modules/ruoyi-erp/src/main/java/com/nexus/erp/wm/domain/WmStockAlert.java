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
 * 安全库存预警对象 wm_stock_alert
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("wm_stock_alert")
public class WmStockAlert extends TenantEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "alert_id")
    private Long alertId;

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

    @ExcelProperty("仓库ID")
    private Long warehouseId;

    @ExcelProperty("仓库编码")
    private String warehouseCode;

    @ExcelProperty("仓库名称")
    private String warehouseName;

    @ExcelProperty("当前库存")
    private BigDecimal currentQty;

    @ExcelProperty("安全库存")
    private BigDecimal safetyStock;

    @ExcelProperty("最低库存")
    private BigDecimal minStock;

    @ExcelProperty("最高库存")
    private BigDecimal maxStock;

    @ExcelProperty("预警类型")
    private String alertType;

    @ExcelProperty("预警级别")
    private String alertLevel;

    @ExcelProperty("处理状态")
    private Integer handledFlag;

    private Date handledTime;

    private Long handledBy;

    @ExcelProperty("处理说明")
    private String handledRemark;

    @TableLogic
    private String delFlag;

    @ExcelProperty("备注")
    private String remark;
}
