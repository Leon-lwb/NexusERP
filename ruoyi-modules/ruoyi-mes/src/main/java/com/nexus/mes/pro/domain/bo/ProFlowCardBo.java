package com.nexus.mes.pro.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.pro.domain.ProFlowCard;

import java.math.BigDecimal;

/**
 * MES流转卡业务对象 pro_flow_card
 * <p>属于 MES 生产管理模块，承载 流转卡 的增改入参。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ProFlowCard.class, reverseConvertGenerate = false)
public class ProFlowCardBo extends BaseEntity {

    /** 流转卡ID */
    private Long cardId;

    /** 流转卡编号 */
    private String cardNo;

    /** 关联MES工单ID */
    @NotNull(message = "MES工单ID不能为空")
    private Long mesWoId;

    /** 物料ID */
    private Long materialId;

    /** 计划数量 */
    @NotNull(message = "计划数量不能为空")
    private BigDecimal plannedQty;

    /** 流转卡状态 */
    private String cardStatus;

    /** 流转卡条码 */
    private String barcode;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}
