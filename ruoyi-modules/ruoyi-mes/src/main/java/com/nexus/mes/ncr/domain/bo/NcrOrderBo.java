package com.nexus.mes.ncr.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.ncr.domain.NcrOrder;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES NCR不合格品单业务对象 ncr_order
 * <p>属于 MES NCR 不合格品管理模块，承载 NCR 单的增改入参。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = NcrOrder.class, reverseConvertGenerate = false)
public class NcrOrderBo extends BaseEntity {

    /** NCR单ID */
    private Long ncrId;

    /** NCR单编号(查询用) */
    private String ncrCode;

    /** 来源类型 */
    @NotNull(message = "来源类型不能为空")
    private String sourceType;

    /** 来源单据ID(查询用) */
    private Long sourceId;

    /** 关联工单ID(查询用) */
    private Long woId;

    /** 关联工单编号 */
    private String woNo;

    /** 关联流转卡ID */
    private Long cardId;

    /** 关联工序报工记录ID */
    private Long stepRecordId;

    /** 工序ID */
    private Long processId;

    /** 工序名称 */
    private String processName;

    /** 物料ID(查询用) */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 批次ID */
    private Long lotId;

    /** 批次编码 */
    private String lotCode;

    /** 不良数量 */
    @NotNull(message = "不良数量不能为空")
    private BigDecimal defectQty;

    /** 处置方式 */
    private String disposition;

    /** 状态(查询用) */
    private String status;

    /** 评审人ID */
    private Long reviewBy;

    /** 评审时间 */
    private Date reviewTime;

    /** 处置人ID */
    private Long dispositionBy;

    /** 处置时间 */
    private Date dispositionTime;

    /** 处置签名文件ID */
    private Long dispositionSignatureId;

    /** 创建时间 */
    private Date createdTime;

    /** 关闭时间 */
    private Date closedTime;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}