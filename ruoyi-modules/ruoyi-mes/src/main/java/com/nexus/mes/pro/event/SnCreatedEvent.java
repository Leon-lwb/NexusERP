package com.nexus.mes.pro.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * SN创建事件
 * <p>ProSnServiceImpl 在 createSn/generateSnForCard 时, 若调用方传入 lotId 则发布本事件;
 * 由 lt 模块 {@code LtSnBindingEventListener} 监听, 写入 lt_sn_binding(SN↔批次绑定)。
 * 事件驱动避免 pro->lt 构造注入成环(lt 已依赖 pro: LtSnBindingServiceImpl 注入 ProSnMapper)。</p>
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
