package com.nexus.core.wm.domain;

    import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 到货通知单行对象 wm_arrival_notice_line
 *
 * @author nexus
 * @date 2025-12-04
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.wm.mapper.WmArrivalNoticeLineMapper.WmArrivalNoticeLineResult")
public class WmArrivalNoticeLine extends BaseEntity {
private static final long serialVersionUID = 1L;

    /** 行ID */
        @TableId(value = "line_id", type = IdType.AUTO)
    private Long lineId;

    /** 通知单ID */
            @Excel(name = "通知单ID")
    private Long noticeId;

    /** 产品物料ID */
            @Excel(name = "产品物料ID")
    private Long materialId;

    /** 产品物料编码 */
            @Excel(name = "产品物料编码")
    private String materialCode;

    /** 产品物料名称 */
            @Excel(name = "产品物料名称")
    private String materialName;

    /** 规格型号 */
            @Excel(name = "规格型号")
    private String materialSpec;

    /** 单位 */
            @Excel(name = "单位")
    private String baseUomId;

    /** 单位名称 */
            @Excel(name = "单位名称")
    private String baseUomName;

    /** 到货数量 */
            @Excel(name = "到货数量")
    private BigDecimal quantityArrival;

    /** 合格数量 */
            @Excel(name = "合格数量")
    private BigDecimal quantityQuanlified;

    /** 是否来料检验 */
            @Excel(name = "是否来料检验")
    private String iqcCheck;

    /** 来料检验单ID */
            @Excel(name = "来料检验单ID")
    private Long iqcId;

    /** 来料检验单编号 */
            @Excel(name = "来料检验单编号")
    private String iqcCode;

    /** 预留字段1 */
    private String attr1;

    /** 预留字段2 */
    private String attr2;

    /** 预留字段3 */
    private Long attr3;

    /** 预留字段4 */
    private Long attr4;

}