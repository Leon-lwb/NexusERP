package com.nexus.mes.eq.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.eq.domain.EqSparePart;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * MES备件主数据视图对象 eq_spare_part
 *
 * <p>属于MES 设备管理模块，备件零件视图对象（查询出参）。</p>
 * <p>MES-EQ-04：{@link #erpAvailableStock} 为 ERP 可用量（在库 − 已预留，且 {@code frozen_flag = 0}），
 * 是低库存判定与采购建议的唯一口径；{@link #currentStock} 仅作为 ERP 库存的派生冗余缓存展示。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EqSparePart.class)
public class EqSparePartVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 备件ID */
    private Long spareId;

    /** 备件编码 */
    @ExcelProperty("备件编码")
    private String spareCode;

    /** 备件名称 */
    @ExcelProperty("备件名称")
    private String spareName;

    /** 规格型号 */
    @ExcelProperty("规格型号")
    private String spec;

    /** 型号 */
    private String model;

    /** 单位 */
    @ExcelProperty("单位")
    private String unit;

    /** 分类 */
    @ExcelProperty("分类")
    private String category;

    /** 安全库存 */
    @ExcelProperty("安全库存")
    private BigDecimal safetyStock;

    /** 当前库存(ERP 库存的派生冗余缓存) */
    @ExcelProperty("当前库存")
    private BigDecimal currentStock;

    /** ERP物料ID(bd_material.material_id) */
    private Long materialId;

    /** ERP仓库ID(wm_warehouse.warehouse_id) */
    private Long warehouseId;

    /** 备件参考单价(成本口径, 人工维护; 出入库过账时随消息下发给 ERP 做移动平均计价) */
    @ExcelProperty("参考单价")
    private BigDecimal unitCost;

    /** ERP可用量(在库 − 已预留, 且 frozen_flag = 0); 未映射物料时为 null */
    private BigDecimal erpAvailableStock;

    /** 是否已映射 ERP 物料 */
    private Boolean erpMapped;

    /** 低库存判定口径: ERP_AVAILABLE(读 ERP 可用量) / NOT_MAPPED(未映射, 无法判定) */
    private String lowStockBasis;

    /** 是否低于安全库存(按 ERP 可用量判定) */
    private Boolean lowStock;

    /** 备注 */
    private String remark;
}
