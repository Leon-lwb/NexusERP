package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmArrivalNoticeLine;

import java.math.BigDecimal;

/**
 * 到货通知单行业务对象 wm_arrival_notice_line
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmArrivalNoticeLine.class, reverseConvertGenerate = false)
public class WmArrivalNoticeLineBo extends BaseEntity {

    /**
     * 行ID
     */
    private Long lineId;

    /**
     * 到货通知ID
     */
    private Long noticeId;

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
     * 物料规格
     */
    private String materialSpec;

    /**
     * 基本单位ID
     */
    private String baseUomId;

    /**
     * 基本单位名称
     */
    private String baseUomName;

    /**
     * 到货数量
     */
    private BigDecimal quantityArrival;

    /**
     * 合格数量
     */
    private BigDecimal quantityQuanlified;

    /**
     * 质检结果
     */
    private String iqcCheck;

    /**
     * 质检单ID
     */
    private Long iqcId;

    /**
     * 质检单号
     */
    private String iqcCode;

    /**
     * 备注
     */
    private String remark;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;

    /**
     * 批量操作ID集合
     */
    private Long[] ids;
}
