package com.nexus.mes.pro.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@ExcelIgnoreUnannotated
public class MesMbomVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "MBOM ID")
    private Long mbomId;

    /** 关联ERP BOM ID */
    @ExcelProperty(value = "ERP BOM ID")
    private Long bomId;

    /** 工艺路线ID */
    private Long routingId;

    /** MBOM版本号 */
    @ExcelProperty(value = "MBOM版本")
    private String mbomVersion;

    /** MBOM状态 */
    @ExcelProperty(value = "状态")
    private String mbomStatus;

    /** 备注 */
    private String remark;

    // === 以下为富集字段(跨库查询填充) ===

    /** ERP BOM版本号 */
    @ExcelProperty(value = "ERP BOM版本")
    private String bomVersion;

    /** 父项物料编码 */
    @ExcelProperty(value = "父项物料编码")
    private String parentMaterialCode;

    /** 父项物料名称 */
    @ExcelProperty(value = "父项物料名称")
    private String parentMaterialName;

    /** 工艺路线编码 */
    @ExcelProperty(value = "工艺路线编码")
    private String routingCode;

    /** 工艺路线名称 */
    @ExcelProperty(value = "工艺路线名称")
    private String routingName;

    /** MBOM明细行 */
    private List<MesMbomLineVo> lines;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
