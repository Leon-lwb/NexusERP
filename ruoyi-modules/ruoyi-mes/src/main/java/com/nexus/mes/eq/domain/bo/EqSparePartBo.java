package com.nexus.mes.eq.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
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
    private String spareCode;

    /** 备件名称 */
    @NotBlank(message = "备件名称不能为空")
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
    private BigDecimal safetyStock;

    /** ERP物料ID(bd_material.material_id); 空表示暂不映射(严格模式下无法出入库) */
    private Long materialId;

    /** ERP仓库ID(wm_warehouse.warehouse_id); 过账到 ERP 仓储的目标仓库 */
    private Long warehouseId;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}
