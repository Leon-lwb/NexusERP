package com.nexus.mes.eq.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * MES备件主数据对象 eq_spare_part
 * <p>
 * <b>账本口径（MES-EQ-04 MVP）</b>：ERP 仓储（{@code wm_material_stock}）是<b>唯一实物账本</b>。
 * <ul>
 *   <li>{@link #materialId} 把备件映射到 ERP 物料（{@code bd_material}），是过账与可用量查询的前提；</li>
 *   <li>{@link #warehouseId} 指定备件在 ERP 中的存放仓库，过账到 {@code wm_material_stock} 时必填；</li>
 *   <li>{@link #currentStock} 为 <b>ERP 库存的派生冗余缓存（只读视图语义）</b>：
 *       同步方向 = ERP → MES，时机 = 每次备件出入库过账后由 {@code EqSparePartServiceImpl} 刷新；
 *       不再作为权威账本，查询可用量一律读 ERP（在库 − 已预留，且 {@code frozen_flag = 0}）。
 *       历史遗留：升级前的数据由增量脚本 {@code 2026-09-20-mes-spare-erp.sql} 标记为待对账。</li>
 * </ul>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eq_spare_part")
public class EqSparePart extends TenantEntity {

    /** 备件ID(主键) */
    @TableId(value = "spare_id")
    private Long spareId;

    /** 备件编码(唯一) */
    private String spareCode;

    /** 备件名称 */
    private String spareName;

    /** 规格型号 */
    private String spec;

    /** 型号 */
    private String model;

    /** 单位 */
    private String unit;

    /** 分类(字典 mes_spare_category) */
    private String category;

    /** 安全库存 */
    private BigDecimal safetyStock;

    /** 当前库存(ERP 库存的派生冗余缓存, 只读视图语义; 权威账本在 wm_material_stock) */
    private BigDecimal currentStock;

    /** ERP物料ID(bd_material.material_id), NULL 表示未映射 */
    private Long materialId;

    /** ERP仓库ID(wm_warehouse.warehouse_id), 过账到 ERP 仓储的目标仓库 */
    private Long warehouseId;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
