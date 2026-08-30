package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 上料交接单对象 wm_material_handover (M3 批次2)
 * <p>
 * 仓库 → 线边库位的物料交接: PC 建单(来源工单+源库位→线边库位), 材料员 PDA 行级核销,
 * 产线操作工 PDA 扫单签收, 签收即过账(逐行 源库位出 + 线边库位入)。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_material_handover")
public class WmMaterialHandover extends TenantEntity {

    /** 状态: 待核销/待签收 */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 状态: 已签收(过账完成) */
    public static final String STATUS_SIGNED = "SIGNED";

    /** 交接单ID */
    @TableId(value = "handover_id")
    private Long handoverId;

    /** 交接单号 */
    private String handoverNo;

    /** 来源工单ID(prd_work_order) */
    private Long woId;

    /** 来源工单号 */
    private String woNo;

    /** 源仓库ID */
    private Long warehouseId;

    /** 源仓库编码 */
    private String warehouseCode;

    /** 源仓库名称 */
    private String warehouseName;

    /** 状态 DRAFT/SIGNED */
    private String status;

    /** 交接时间(建单时间) */
    private Date handoverTime;

    /** 签收人ID */
    private Long signBy;

    /** 签收人 */
    private String signName;

    /** 签收时间 */
    private Date signTime;

    /** 备注 */
    private String remark;

    /** 行列表(非表字段) */
    @TableField(exist = false)
    private List<WmMaterialHandoverLine> lines;
}
