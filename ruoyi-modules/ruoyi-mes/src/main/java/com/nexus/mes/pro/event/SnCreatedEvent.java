package com.nexus.mes.pro.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * SN创建事件
 * <p>ProSnServiceImpl 在 createSn/generateSnForCard 造码时发布本事件(lotId 可空):
 * lt 模块 {@code LtSnBindingEventListener} 监听(lotId 非空时写 lt_sn_binding SN↔批次绑定),
 * label 模块 {@code SnLabelPrintEventListener} 监听(AFTER_COMMIT 打印 SN 标签)。
 * 事件驱动避免 pro->lt/label 构造注入成环。</p>
 *
 * @author Nexus
 */
@Getter
@RequiredArgsConstructor
public class SnCreatedEvent {

    /** SN ID */
    private final Long snId;

    /** SN编码 */
    private final String snCode;

    /** 批次ID(关联 lt_lot) */
    private final Long lotId;

    /** 物料ID */
    private final Long materialId;
}
