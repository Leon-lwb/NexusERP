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

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 预警ID
     */
    @TableId(value = "alert_id")
    private Long alertId;

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
     * 仓库ID
     */
    @ExcelProperty("仓库ID")
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
     * 当前库存
     */
    @ExcelProperty("当前库存")
    private BigDecimal currentQty;

    /**
     * 安全库存
     */
    @ExcelProperty("安全库存")
    private BigDecimal safetyStock;

    /**
     * 最低库存
     */
    @ExcelProperty("最低库存")
    private BigDecimal minStock;

    /**
     * 最高库存
     */
    @ExcelProperty("最高库存")
    private BigDecimal maxStock;

    /**
     * 预警类型
     */
    @ExcelProperty("预警类型")
    private String alertType;

    /**
     * 预警级别
     */
    @ExcelProperty("预警级别")
    private String alertLevel;

    /**
     * 处理状态
     */
    @ExcelProperty("处理状态")
    private Integer handledFlag;

    /**
     * 处理时间
     */
    private Date handledTime;

    /**
     * 处理人ID
     */
    private Long handledBy;

    /**
     * 处理说明
     */
    @ExcelProperty("处理说明")
    private String handledRemark;

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
