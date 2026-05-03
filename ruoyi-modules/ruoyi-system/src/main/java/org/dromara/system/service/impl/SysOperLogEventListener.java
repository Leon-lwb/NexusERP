package org.dromara.system.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.ip.AddressUtils;
import org.dromara.common.log.event.OperLogEvent;
import org.dromara.system.domain.bo.SysOperLogBo;
import org.dromara.system.service.ISysOperLogService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 操作日志事件监听器 — 独立于 ServiceImpl，避免 JDK 动态代理无法解析 @EventListener 的问题
 *
 * @author nexus-erp
 */
@Component
@RequiredArgsConstructor
public class SysOperLogEventListener {

    private final ISysOperLogService operLogService;

    @Async
    @EventListener
    public void recordOper(OperLogEvent operLogEvent) {
        SysOperLogBo operLog = MapstructUtils.convert(operLogEvent, SysOperLogBo.class);
        operLog.setOperLocation(AddressUtils.getRealAddressByIP(operLog.getOperIp()));
        operLogService.insertOperlog(operLog);
    }
}
