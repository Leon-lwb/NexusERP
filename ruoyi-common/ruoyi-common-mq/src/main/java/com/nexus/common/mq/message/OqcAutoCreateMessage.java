package com.nexus.common.mq.message;

import com.nexus.common.mq.domain.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * ERP 销售出库审批通过消息
 * <p>
 * 触发点：WmSalesIssueServiceImpl.afterFlowHandle 在 ActStatus.pass 后 afterCommit 投递。
 * 消费端：MES OqcAutoCreateConsumer 自动创建 OQC 出货检验单。
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OqcAutoCreateMessage extends BaseMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    /** ERP 销售订单头 ID */
    private Long soId;

    /** 物料 ID（取自销售出库行 wm_sales_issue_line） */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 出库数量（取自销售出库行 quantity_issue） */
    private BigDecimal quantity;
}
