package com.nexus.common.mq.message;

import com.nexus.common.mq.domain.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * MES 委外SN生成消息 (ERP 委外发料下达 -> MES 批量生成 PCBA SN)
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SubcontractSnGenerateMessage extends BaseMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 委外订单ID */
    private Long subcontractId;

    /** 委外单号 */
    private String subcontractNo;

    /** 产出物料ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 本次生成SN数量 */
    private BigDecimal quantity;
}
