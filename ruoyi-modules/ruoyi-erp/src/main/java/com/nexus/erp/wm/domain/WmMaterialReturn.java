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
 * 生产退料单对象 wm_material_return (M2 批次2)
 * <p>
 * 退料三类: 良品(回可用库存)/不良品(回指定库区)/报废(仅留痕); PC 建单, PDA 扫码核销。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_material_return")
public class WmMaterialReturn extends TenantEntity {

    /** 退料类型: 良品 */
    public static final String TYPE_GOOD = "GOOD";
    /** 退料类型: 不良品 */
    public static final String TYPE_DEFECT = "DEFECT";
    /** 退料类型: 报废 */
    public static final String TYPE_SCRAP = "SCRAP";

    /** 状态: 待核销 */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 状态: 已完成 */
    public static final String STATUS_COMPLETED = "COMPLETED";

    /** 退料单ID */
    @TableId(value = "return_id")
    private Long returnId;

    /** 退料单号 */
    private String returnNo;

    /** 仓库ID */
    private Long warehouseId;

    /** 仓库编码 */
    private String warehouseCode;

    /** 仓库名称 */
    private String warehouseName;

    /** 退料类型 GOOD/DEFECT/SCRAP */
    private String returnType;

    /** 来源单号(工单/领料单等) */
    private String sourceDoc;

    /** 状态 DRAFT/COMPLETED */
    private String status;

    /** 退料日期 */
    private Date returnDate;

    /** 备注 */
    private String remark;

    /** 行列表(非表字段) */
    @TableField(exist = false)
    private List<WmMaterialReturnLine> lines;
}
