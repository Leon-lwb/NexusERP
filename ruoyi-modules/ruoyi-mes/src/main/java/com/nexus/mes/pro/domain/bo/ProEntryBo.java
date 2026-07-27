package com.nexus.mes.pro.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;

/**
 * MES工序进站业务对象(P0-3)
 * <p>承载 工序进站(entry) 的校验上下文与开工人参。</p>
 * <p>toolingId/lotId/materialId 为可选上下文--操作员扫码带入,供 TOOLING_AVAILABLE/LOT_AVAILABLE 规则校验；
 * 为空时对应规则跳过(返回 PASS)。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProEntryBo extends BaseEntity {

    /** 流转卡ID */
    @NotNull(message = "流转卡不能为空")
    private Long cardId;

    /** 工序记录ID */
    @NotNull(message = "工序记录不能为空")
    private Long stepRecordId;

    /** 操作员ID */
    @NotNull(message = "操作员不能为空")
    private Long operatorId;

    /** 工装ID(可选,操作员扫码带入,TOOLING_AVAILABLE规则校验用) */
    private Long toolingId;

    /** 批次ID(可选,操作员扫码带入,LOT_AVAILABLE规则校验用) */
    private Long lotId;

    /** 物料ID(可选,与lotId配合校验批次物料匹配) */
    private Long materialId;
}
