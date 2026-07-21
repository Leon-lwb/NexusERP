package com.nexus.erp.wm.domain.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import io.github.linpeilie.annotations.AutoMapper;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmStockAlert;

import java.math.BigDecimal;

/**
 * 安全库存预警业务对象
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmStockAlert.class, reverseConvertGenerate = false)
public class WmStockAlertBo extends BaseEntity {

    /**
     * 预警ID
     */
    private Long alertId;

    /**
     * 物料ID
     */
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
     * 单位名称
     */
    private String baseUomName;

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
     * 当前库存
     */
    private BigDecimal currentQty;

    /**
     * 安全库存
     */
    private BigDecimal safetyStock;

    /**
     * 最低库存
     */
    private BigDecimal minStock;

    /**
     * 最高库存
     */
    private BigDecimal maxStock;

    /**
     * 预警类型
     */
    private String alertType;

    /**
     * 预警级别
     */
    private String alertLevel;

    /**
     * 处理状态
     */
    private Integer handledFlag;

    /**
     * 处理说明
     */
    private String handledRemark;

    /**
     * 备注
     */
    private String remark;

    /**
     * 批量操作ID集合
     */
    private Long[] ids;
}
