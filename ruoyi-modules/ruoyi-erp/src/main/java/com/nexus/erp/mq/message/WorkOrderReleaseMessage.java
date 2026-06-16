package com.nexus.erp.mq.message;

/**
 * @deprecated 已迁移至 ruoyi-common-mq 模块: com.nexus.common.mq.message.WorkOrderReleaseMessage
 * <p>
 * 此类保留仅作向后兼容，ERP侧代码应逐步迁移到公共模块的WorkOrderReleaseMessage。
 * 新增字段(routingId)仅在公共模块版本中可用。
 *
 * @see com.nexus.common.mq.message.WorkOrderReleaseMessage
 */
@Deprecated
public class WorkOrderReleaseMessage extends com.nexus.common.mq.message.WorkOrderReleaseMessage {
}
