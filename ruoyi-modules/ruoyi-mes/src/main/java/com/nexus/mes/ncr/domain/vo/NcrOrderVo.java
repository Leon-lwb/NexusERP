package com.nexus.mes.ncr.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.translation.annotation.Translation;
import com.nexus.common.translation.constant.TransConstant;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.ncr.domain.NcrOrder;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * MES NCR不合格品单视图对象 ncr_order
 * <p>属于 MES NCR 不合格品管理模块，作为 NCR 单的查询/导出返回结构。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = NcrOrder.class)
public class NcrOrderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** NCR单ID */
    @ExcelProperty("NCR单ID")
    private Long ncrId;

    /** NCR单编号 */
    @ExcelProperty("NCR单编号")
    private String ncrCode;

    /** 来源类型 */
    @ExcelProperty("来源类型")
    private String sourceType;

    /** 来源单据ID */
    private Long sourceId;

    /** 关联工单ID */
    private Long woId;

    /** 关联工单编号 */
    @ExcelProperty("关联工单")
    private String woNo;

    /** 关联流转卡ID */
    private Long cardId;

    /** 关联工序报工记录ID */
    private Long stepRecordId;

    /** 工序ID */
    private Long processId;

    /** 工序名称 */
    @ExcelProperty("工序")
    private String processName;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    @ExcelProperty("物料编码")
    private String materialCode;

    /** 物料名称 */
    @ExcelProperty("物料名称")
    private String materialName;

    /** 批次ID */
    private Long lotId;

    /** 批次编码 */
    @ExcelProperty("批次编码")
    private String lotCode;

    /** 不良数量 */
    @ExcelProperty("不良数量")
    private BigDecimal defectQty;

    /** 处置方式 */
    @ExcelProperty("处置方式")
    private String disposition;

    /** 状态 */
    @ExcelProperty("状态")
    private String status;

    /** 评审人ID */
    private Long reviewBy;

    /** 评审人名称(由 @Translation 按 reviewBy 自动翻译) */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "reviewBy")
    private String reviewByName;

    /** 评审时间 */
    @ExcelProperty("评审时间")
    private Date reviewTime;

    /** 处置人ID */
    private Long dispositionBy;

    /** 处置人名称(由 @Translation 按 dispositionBy 自动翻译) */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "dispositionBy")
    private String dispositionByName;

    /** 处置时间 */
    @ExcelProperty("处置时间")
    private Date dispositionTime;

    /** 处置签名文件ID */
    private Long dispositionSignatureId;

    /** 创建时间 */
    @ExcelProperty("创建时间")
    private Date createdTime;

    /** 关闭时间 */
    @ExcelProperty("关闭时间")
    private Date closedTime;

    /** 备注 */
    private String remark;

    // ==================== 嵌套明细(非DB) ====================

    /** 缺陷明细列表 */
    private List<NcrDefectVo> defects;

    /** 返工单列表 */
    private List<NcrReworkOrderVo> reworkOrders;
}