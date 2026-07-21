package com.nexus.mes.pro.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.pro.domain.ProFlowCard;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * MES流转卡视图对象 pro_flow_card
 * <p>属于 MES 生产管理模块，作为 流转卡 的查询/导出返回结构。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ProFlowCard.class)
public class ProFlowCardVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流转卡ID */
    @ExcelProperty("流转卡ID")
    private Long cardId;

    /** 流转卡编号 */
    @ExcelProperty("流转卡编号")
    private String cardNo;

    /** 关联MES工单ID */
    private Long mesWoId;

    /** 工单编号 */
    @ExcelProperty("工单编号")
    private String woNo;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    @ExcelProperty("产品编码")
    private String materialCode;

    /** 物料名称 */
    @ExcelProperty("产品名称")
    private String materialName;

    /** 计划数量 */
    @ExcelProperty("计划数量")
    private BigDecimal plannedQty;

    /** 已完成数量 */
    @ExcelProperty("已完成数量")
    private BigDecimal completedQty;

    /** 报废数量 */
    @ExcelProperty("报废数量")
    private BigDecimal scrapQty;

    /** 当前工序步骤记录ID */
    private Long currentStepId;

    /** 当前工序序号 */
    private Integer currentStepSeq;

    /** 流转卡状态 */
    @ExcelProperty("状态")
    private String cardStatus;

    /** 流转卡条码 */
    private String barcode;

    /** 备注 */
    private String remark;

    /** 工序步骤列表(详情查询时填充) */
    private List<ProFlowCardStepVo> steps;
}
