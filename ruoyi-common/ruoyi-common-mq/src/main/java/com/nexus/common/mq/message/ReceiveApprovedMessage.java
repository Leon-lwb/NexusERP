package com.nexus.common.mq.message;

import com.nexus.common.mq.domain.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * ERP 收货单审批通过消息
 * <p>
 * 触发点：WmMaterialReceiveCrudServiceImpl.afterFlowHandle 在 ActStatus.pass 后 afterCommit 投递。
 * 消费端：MES IqcAutoCreateConsumer 自动创建 IQC 检验单。
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReceiveApprovedMessage extends BaseMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    /** ERP 收货单 ID */
    private Long receiveId;

    /** 收货单号 */
    private String receiveCode;

    /** 物料 ID（取自收货明细首条，明细表 wm_material_receive_detail） */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 供应商 ID（来自收货单主表 wm_material_receive） */
    private Long vendorId;

    /** 供应商名称 */
    private String vendorName;

    /** 收货数量（取自收货明细首条 quantity 字段） */
    private BigDecimal quantity;

    /** 收货明细 ID（用于 IQC 关联，可空） */
    private Long receiveDetailId;
}
