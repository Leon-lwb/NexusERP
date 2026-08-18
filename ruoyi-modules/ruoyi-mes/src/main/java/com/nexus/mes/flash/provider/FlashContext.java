package com.nexus.mes.flash.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 刷写执行上下文(SN/工单/工序/刷写类型)
 *
 * @author Nexus
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashContext {

    private Long snId;
    private String snCode;
    private Long mesWoId;
    private String woNo;
    private Long cardId;
    private Long stepRecordId;
    private Long processId;
    private String processCode;
    private String processName;
    private Long flashTypeId;
    private String flashTypeCode;

    /** SN 物料ID(固件类物料匹配校验用) */
    private Long snMaterialId;

    /** 入参内容引用ID(固件类=固件ID, 由 Bo.contentRefId 传入) */
    private Long contentRefId;
}
