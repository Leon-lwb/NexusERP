package com.nexus.ai.engineering.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.ai.engineering.domain.EngineeringTaskVo;
import com.nexus.ai.engineering.security.EngineeringAccess;
import com.nexus.ai.engineering.service.*;
import com.nexus.common.ai.engineering.EngineeringTaskCommand;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.tenant.helper.TenantHelper;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 浏览器工程模板和本人任务接口，使用现有 Sa-Token 权限。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/engineering")
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringTaskController {
    private final EngineeringTaskService tasks;
    private final EngineeringTemplateCatalog templates;
    private final EngineeringAccess access;

    /** 返回已审模板目录，空目录明确表示暂不支持生成。 */
    @GetMapping("/templates")
    @SaCheckPermission("ai:engineering:query")
    public R<List<EngineeringTemplateCatalog.Template>> templates() {
        access.current("ai:engineering:query", false);
        return R.ok(templates.list());
    }

    /** 创建有界工程任务，身份和租户不从请求正文读取。 */
    @PostMapping("/tasks")
    @SaCheckPermission("ai:engineering:task")
    public R<EngineeringTaskVo> create(@RequestBody EngineeringTaskCommand command) {
        var actor = access.current("ai:engineering:task", true);
        return TenantHelper.dynamic(
                actor.tenantId(), () -> R.ok(tasks.createDraft(actor, command)));
    }

    /** 按当前用户与可选会话查询任务。 */
    @GetMapping("/tasks")
    @SaCheckPermission("ai:engineering:query")
    public TableDataInfo<EngineeringTaskVo> list(
            @RequestParam(required = false) Long sessionId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        var actor = access.current("ai:engineering:query", false);
        return TenantHelper.dynamic(
                actor.tenantId(), () -> tasks.list(actor, sessionId, pageNum, pageSize));
    }

    /** 查询本人任务详情和真实阶段记录。 */
    @GetMapping("/tasks/{taskId}")
    @SaCheckPermission("ai:engineering:query")
    public R<EngineeringTaskVo> detail(@PathVariable Long taskId) {
        var actor = access.current("ai:engineering:query", false);
        return TenantHelper.dynamic(actor.tenantId(), () -> R.ok(tasks.detail(actor, taskId)));
    }

    /** 取消未领取任务，或向当前执行设备发送停止请求。 */
    @PostMapping("/tasks/{taskId}/cancel")
    @SaCheckPermission("ai:engineering:task")
    public R<EngineeringTaskVo> cancel(@PathVariable Long taskId) {
        var actor = access.current("ai:engineering:task", false);
        return TenantHelper.dynamic(actor.tenantId(), () -> R.ok(tasks.cancel(actor, taskId)));
    }
}
