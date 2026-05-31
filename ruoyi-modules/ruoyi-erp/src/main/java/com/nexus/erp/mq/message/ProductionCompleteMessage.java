package com.nexus.erp.mq.message;

import com.nexus.common.mq.domain.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 生产完工消息，用于异步触发倒冲领料和成品入库
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductionCompleteMessage extends BaseMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工单ID */
    private Long woId;
    /** 完工数量 */
    private BigDecimal completionQty;
    /** 产品物料ID */
    private Long materialId;
    /** 产品编码 */
    private String materialCode;
    /** 产品名称 */
    private String materialName;
    /** 车间ID（作为默认入库仓库） */
    private Long workshopId;
}
