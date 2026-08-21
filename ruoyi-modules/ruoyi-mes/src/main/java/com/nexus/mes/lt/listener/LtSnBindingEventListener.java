package com.nexus.mes.lt.listener;

import com.nexus.mes.lt.domain.bo.LtSnBindingBo;
import com.nexus.mes.lt.service.ILtSnBindingService;
import com.nexus.mes.pro.event.SnCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * SN创建事件监听器
 * <p>监听 {@link SnCreatedEvent}, 在 SN 创建并指定批次时, 写入 lt_sn_binding(SN↔批次绑定)。
 * 默认 {@code @EventListener} 同步同事务: SN 与绑定原子写入, 绑定失败则 SN 一并回滚(符合预期)。
 * 事件驱动避免 pro->lt 构造注入成环(lt 已依赖 pro)。</p>
 *
 * @author Nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LtSnBindingEventListener {

    private final ILtSnBindingService snBindingService;

    /**
     * SN 创建联动写 SN↔批次绑定
     * <p>lotId 为空(未指定批次)时跳过——SnCreatedEvent 现为造码即发(供 label 打印等消费),
     * 批次绑定仅在指定批次时生效。</p>
     *
     * @param event SN创建事件
     */
    @EventListener
    public void onSnCreated(SnCreatedEvent event) {
        if (event.getLotId() == null) {
            return;
        }
        LtSnBindingBo bo = new LtSnBindingBo();
        bo.setSnId(event.getSnId());
        bo.setLotId(event.getLotId());
        bo.setMaterialId(event.getMaterialId());
        snBindingService.insertByBo(bo);
        log.info("SN-批次绑定(SN创建联动): snId={}, lotId={}, materialId={}",
            event.getSnId(), event.getLotId(), event.getMaterialId());
    }
}
