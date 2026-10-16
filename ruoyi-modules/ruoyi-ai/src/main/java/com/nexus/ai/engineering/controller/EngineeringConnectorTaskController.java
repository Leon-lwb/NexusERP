package com.nexus.ai.engineering.controller;

import cn.dev33.satoken.annotation.SaIgnore;

import com.nexus.ai.engineering.domain.*;
import com.nexus.ai.engineering.security.EngineeringConnectorFilter;
import com.nexus.ai.engineering.service.EngineeringTaskService;
import com.nexus.common.core.domain.R;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** 连接器当前设备的任务接口；所有方法强制依赖设备过滤器身份。 */
@SaIgnore
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/engineering/connector")
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringConnectorTaskController {
    private final EngineeringTaskService tasks;

    /** 空闲设备短轮询领取任务，当前无任务返回 data=null。 */
    @PostMapping("/claim")
    public R<EngineeringTaskClaim> claim(HttpServletRequest request) {
        return R.ok(tasks.claim(EngineeringConnectorFilter.principal(request)));
    }

    /** CAS 冲突后恢复读取当前修订号和取消状态，不重新领取。 */
    @GetMapping("/tasks/{taskId}")
    public R<EngineeringTaskVo> detail(HttpServletRequest request, @PathVariable Long taskId) {
        return R.ok(tasks.deviceDetail(EngineeringConnectorFilter.principal(request), taskId));
    }

    /** 续约当前尝试。 */
    @PostMapping("/tasks/{taskId}/renew")
    public R<EngineeringTaskClaim> renew(
            HttpServletRequest request,
            @PathVariable Long taskId,
            @RequestBody EngineeringLease lease) {
        return R.ok(tasks.renew(EngineeringConnectorFilter.principal(request), taskId, lease));
    }

    /** 写入有界阶段和取消确认，同事件重试返回相同回执。 */
    @PostMapping("/tasks/{taskId}/events")
    public R<EngineeringTaskReceipt> event(
            HttpServletRequest request,
            @PathVariable Long taskId,
            @RequestBody EngineeringTaskEvent event) {
        return R.ok(tasks.event(EngineeringConnectorFilter.principal(request), taskId, event));
    }
}
