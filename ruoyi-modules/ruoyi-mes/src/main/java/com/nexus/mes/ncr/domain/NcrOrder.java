package com.nexus.mes.ncr.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES NCR不合格品单对象 ncr_order
 * <p>属于 MES NCR 不合格品管理模块，表达 不合格品处理单 的领域模型。</p>
 * <p>状态机: OPEN -> REVIEW -> DISPOSITION -> IN_REWORK -> CLOSED</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ncr_order")
public class NcrOrder extends TenantEntity {

    /** NCR单ID(主键) */
    @TableId(value = "ncr_id")
    private Long ncrId;

    /** NCR单编号 */
    private String ncrCode;

    /** 来源类型(INSPECTION/QC_PASSBACK/PRODUCTION) */
    private String sourceType;

    /** 来源单据ID */
    private Long sourceId;

    /** 关联工单ID */
    private Long woId;

    /** 关联工单编号(冗余) */
    private String woNo;

    /** 关联流转卡ID */
    private Long cardId;

    /** 关联工序报工记录ID */
    private Long stepRecordId;

    /** 工序ID */
    private Long processId;

    /** 工序名称(冗余) */
    private String processName;

    /** 物料ID */
    private Long materialId;

    /** 物料编码(冗余) */
    private String materialCode;

    /** 物料名称(冗余) */
    private String materialName;

    /** 批次ID */
    private Long lotId;

    /** 批次编码(冗余) */
    private String lotCode;

    /** 不良数量 */
    private BigDecimal defectQty;

    /** 处置方式(REWORK/SCRAP/ACCEPT_AS_IS/RETURN) */
    private String disposition;

    /** 状态: OPEN/REVIEW/DISPOSITION/IN_REWORK/CLOSED */
    private String status;

    /** 评审人ID */
    private Long reviewBy;

    /** 评审时间 */
    private Date reviewTime;

    /** 处置人ID */
    private Long dispositionBy;

    /** 处置时间 */
    private Date dispositionTime;

    /** 处置签名文件ID(关联OSS) */
    private Long dispositionSignatureId;

    /** 创建时间 */
    private Date createdTime;

    /** 关闭时间 */
    private Date closedTime;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}