package com.nexus.system.service.impl;

import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.ip.AddressUtils;
import com.nexus.common.log.event.OperLogEvent;
import com.nexus.system.domain.bo.SysOperLogBo;
import com.nexus.system.service.ISysOperLogService;
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

    /** ISysOperLogService 业务服务，提供 操作日志 相关能力。 */
    private final ISysOperLogService operLogService;

    /**
     * 处理操作日志相关操作。
     * @param operLogEvent 操作日志 参数
     */
    @Async
    @EventListener
    public void recordOper(OperLogEvent operLogEvent) {
        SysOperLogBo operLog = MapstructUtils.convert(operLogEvent, SysOperLogBo.class);
        operLog.setOperLocation(AddressUtils.getRealAddressByIP(operLog.getOperIp()));
        operLogService.insertOperlog(operLog);
    }
}
