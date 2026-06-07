package com.nexus.erp.mq.message;

import com.nexus.common.mq.domain.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 采购收货回写消息，用于异步回写采购订单已收货数量
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseReceiveWritebackMessage extends BaseMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 入库单ID */
    private Long receiveId;

    /**
     * 租户ID（字符串格式）
     * <p>
     * BaseMessage.tenantId 为 Long 类型，"000000" 转 Long 会变成 0，再转回 String 变成 "0"，
     * 与数据库中 tenant_id='000000' 不匹配。因此单独用 String 字段传递原始租户ID。
     * </p>
     */
    private String tenantIdStr;
}
