package com.nexus.mes.dhr.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.mes.dhr.domain.DhrRecord;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * DHR 批记录视图对象（字段与 dhr_record 列一一对应，驼峰）。
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = DhrRecord.class)
public class DhrRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** DHR 主键 */
    private Long dhrId;

    /** DHR 编号 */
    @ExcelProperty("DHR编号")
    private String dhrNo;

    /** MES 工单ID */
    private Long workOrderId;

    /** 工单编号 */
    @ExcelProperty("工单编号")
    private String workOrderNo;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    @ExcelProperty("物料编码")
    private String materialCode;

    /** 物料名称 */
    @ExcelProperty("物料名称")
    private String materialName;

    /** 批次号 */
    @ExcelProperty("批次号")
    private String lotNo;

    /** SN */
    @ExcelProperty("SN")
    private String sn;

    /** 计划数量 */
    @ExcelProperty("计划数量")
    private BigDecimal plannedQty;

    /** 完成数量 */
    @ExcelProperty("完成数量")
    private BigDecimal completedQty;

    /** 报废数量 */
    @ExcelProperty("报废数量")
    private BigDecimal scrapQty;

    /** 状态 */
    @ExcelProperty("状态")
    private String status;

    /** 快照总哈希 */
    @ExcelProperty("快照哈希")
    private String snapshotHash;

    /** 冻结时间 */
    private Date frozenAt;

    /** 冻结人 */
    private Long frozenBy;

    /** 提交时间 */
    @ExcelProperty("提交时间")
    private Date submittedAt;

    /** 提交人 */
    private Long submittedBy;

    /** 提交签名ID */
    private Long submitSignatureId;

    /** 放行时间 */
    @ExcelProperty("放行时间")
    private Date releasedAt;

    /** 放行人 */
    private Long releasedBy;

    /** 放行签名ID */
    private Long releaseSignatureId;

    /** 驳回时间 */
    private Date rejectedAt;

    /** 驳回人 */
    private Long rejectedBy;

    /** 驳回原因 */
    @ExcelProperty("驳回原因")
    private String rejectReason;

    /** 驳回签名ID */
    private Long rejectSignatureId;

    /** 归档时间 */
    private Date archivedAt;

    /** 版本号 */
    private Integer version;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @ExcelProperty("创建时间")
    private Date createTime;

    /** 分册列表（仅详情/构建/重建时填充） */
    private List<DhrSectionVo> sections;
}
