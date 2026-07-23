package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

/**
 * 到货通知单行对象 wm_arrival_notice_line
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_arrival_notice_line")
public class WmArrivalNoticeLine extends TenantEntity {

    /** 行ID */
    @TableId(value = "line_id")
    private Long lineId;

    /** 通知单ID */
    private Long noticeId;
    /** 物料ID */
    private Long materialId;
    /** 物料编码 */
    private String materialCode;
    /** 物料名称 */
    private String materialName;
    /** 规格型号 */
    private String materialSpec;
    /** 单位ID */
    private String baseUomId;
    /** 单位名称 */
    private String baseUomName;
    /** 到货数量 */
    private BigDecimal quantityArrival;
    /** 合格数量 */
    private BigDecimal quantityQuanlified;
    /** 质检标志 */
    private String iqcCheck;
    /** 质检单ID */
    private Long iqcId;
    /** 质检单编号 */
    private String iqcCode;
    /** IQC检验结果(QUALIFIED/UNQUALIFIED, NULL=未完成)——confirmShelf 门禁依据 */
    private String iqcStatus;
    /** 备注 */
    private String remark;
    /** 预留字段1 */
    private String attr1;
    /** 预留字段2 */
    private String attr2;
    /** 预留字段3 */
    private Long attr3;
    /** 预留字段4 */
    private Long attr4;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
