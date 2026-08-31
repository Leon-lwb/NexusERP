package com.nexus.common.mq.message;

import com.nexus.common.mq.domain.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * OQC检验完成消息，MES出货质检完成后发送到ERP
 * <p>
 * 位于 ruoyi-common-mq 供 MES(生产者) 与 ERP(消费者) 跨模块共享。
 * ERP 消费端按 soId + materialId 定位销售订单行，回写 oqcStatus。
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OqcCompletedMessage extends BaseMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    /** OQC检验ID */
    private Long oqcId;
    /** OQC检验编号 */
    private String oqcCode;
    /** ERP销售订单头ID */
    private Long soId;
    /** 物料ID（用于定位销售订单行） */
    private Long materialId;
    /** 物料编码 */
    private String materialCode;
    /** 检验结果: QUALIFIED/UNQUALIFIED */
    private String inspectResult;
    /** 检验数量 */
    private BigDecimal quantity;
}
