package com.nexus.mes.eq.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.eq.domain.EqSparePart;

import java.math.BigDecimal;

/**
 * MES备件主数据业务对象 eq_spare_part
 *
 * <p>属于MES 设备管理模块，备件零件业务对象（新增/编辑入参）。</p>
 * <p>{@code materialId} 为 ERP 物料映射（MES-EQ-04）：新增/编辑时服务端会校验物料存在且状态启用；
 * {@code warehouseId} 为 ERP 仓库映射，过账到 {@code wm_material_stock} 的目标仓库。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = EqSparePart.class, reverseConvertGenerate = false)
public class EqSparePartBo extends BaseEntity {

    /** 备件ID */
    private Long spareId;

    /** 备件编码 */
    @NotBlank(message = "备件编码不能为空")
    @Size(max = 64, message = "备件编码不能超过64个字符")
    private String spareCode;

    /** 备件名称 */
    @NotBlank(message = "备件名称不能为空")
    @Size(max = 128, message = "备件名称不能超过128个字符")
    private String spareName;

    /** 规格型号 */
    private String spec;

    /** 型号 */
    private String model;

    /** 单位 */
    private String unit;

    /** 分类 */
    private String category;

    /** 安全库存 */
    @DecimalMin(value = "0", message = "安全库存不能为负数")
    @Digits(integer = 10, fraction = 4, message = "安全库存最多10位整数、4位小数")
    private BigDecimal safetyStock;

    /** ERP物料ID(bd_material.material_id); 空表示暂不映射(严格模式下无法出入库) */
    private Long materialId;

    /** ERP仓库ID(wm_warehouse.warehouse_id); 过账到 ERP 仓储的目标仓库 */
    private Long warehouseId;

    /**
     * 备件参考单价(成本口径, 人工维护)。
     * <p>仅在备件主数据新增/编辑时维护；出入库过账的单价一律由服务端读库填充，本字段不参与出入库入参。</p>
     */
    @DecimalMin(value = "0", message = "参考单价不能为负数")
    @Digits(integer = 14, fraction = 4, message = "参考单价最多14位整数、4位小数")
    private BigDecimal unitCost;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}
