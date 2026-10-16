package com.nexus.ai.tool;

import com.nexus.ai.engineering.service.*;
import com.nexus.ai.service.sse.AiSseMessageHelper;
import com.nexus.common.ai.engineering.*;
import com.nexus.common.tenant.helper.TenantHelper;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/** 六个工程具名动作；仅创建受控任务，不运行 Shell/JS、不接受模型传入用户身份或本地路径。 */
@Component
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EasyedaEngineeringTool {
    private final EngineeringTaskService tasks;
    private final EngineeringTemplateCatalog catalog;
    private final EngineeringToolContext.Context bound;

    /** 注册到工具管理的入口，调用时必须显式绑定认证上下文。 */
    @Autowired
    public EasyedaEngineeringTool(
            EngineeringTaskService tasks, EngineeringTemplateCatalog catalog) {
        this(tasks, catalog, null);
    }

    private EasyedaEngineeringTool(
            EngineeringTaskService tasks,
            EngineeringTemplateCatalog catalog,
            EngineeringToolContext.Context bound) {
        this.tasks = tasks;
        this.catalog = catalog;
        this.bound = bound;
    }

    /** 构造本次请求专用实例，避免 Agent 框架换线程时丢失或串用身份。 */
    public EasyedaEngineeringTool withContext(EngineeringToolContext.Context context) {
        return new EasyedaEngineeringTool(tasks, catalog, context);
    }

    /** 检查连接器和编辑器的实际版本、状态，不承诺已创建工程。 */
    @Tool("检查本地嘉立创EDA连接，返回异步任务编号；不是设计完成")
    public String easyeda_check_connection() {
        return dispatch(EngineeringAction.CHECK_CONNECTION, null, null, null);
    }

    /** 打开本人原任务的工程，禁止输入任意文件系统路径。 */
    @Tool("打开本人已有草稿任务对应的工程，只接受原taskId，不接受路径")
    public String easyeda_open_project(@P("本人原工程任务编号") String parentTaskId) {
        return child(EngineeringAction.OPEN_PROJECT, parentTaskId);
    }

    /** 从已审模板创建草稿，尺寸默认值与版本摘要均来自服务端目录。 */
    @Tool("从已审模板创建PCB草稿任务，参数只允许boardWidthMm/boardHeightMm十进制字符串、layers整数与optionalModules；结果仅表示已派发")
    public String easyeda_create_pcb_draft(
            @P("服务端已审模板标识") String templateId,
            @P("允许的板参数，不得包含路径、脚本、URL或身份") Map<String, Object> parameters) {
        return dispatch(EngineeringAction.CREATE_PCB_DRAFT, null, templateId, parameters);
    }

    /** 导出本人已有工程的预览、BOM、DRC 和模型，不直接访问本地文件。 */
    @Tool("导出本人原工程的私有设计产物，返回异步任务编号")
    public String easyeda_export_pcb_artifacts(@P("本人原工程任务编号") String parentTaskId) {
        return child(EngineeringAction.EXPORT_ARTIFACTS, parentTaskId);
    }

    /** 运行真实设计规则检查，空报告不等于通过。 */
    @Tool("对本人原工程运行真实DRC，返回异步任务编号；报告未返回前不能宣称通过")
    public String easyeda_run_drc(@P("本人原工程任务编号") String parentTaskId) {
        return child(EngineeringAction.RUN_DRC, parentTaskId);
    }

    /** 固化草稿归档，不创建 ERP 物料、不触发生产发布。 */
    @Tool("将本人已有工程固化为私有设计草稿归档，不发布生产，不代表可制造")
    public String easyeda_submit_design_draft(@P("本人原工程任务编号") String parentTaskId) {
        return child(EngineeringAction.SUBMIT_DRAFT, parentTaskId);
    }

    private String child(EngineeringAction action, String parent) {
        if (parent == null || !parent.matches("[1-9][0-9]{0,18}"))
            return response("NEEDS_INPUT", "NEEDS_INPUT", "请选择本人原工程任务", null);
        try {
            return dispatch(action, Long.valueOf(parent), null, null);
        } catch (NumberFormatException e) {
            return response("NEEDS_INPUT", "NEEDS_INPUT", "原任务编号无效", null);
        }
    }

    private String dispatch(
            EngineeringAction action,
            Long parent,
            String templateId,
            Map<String, Object> parameters) {
        try {
            var context = bound == null ? EngineeringToolContext.current() : bound;
            String version = null, hash = null;
            if (action == EngineeringAction.CREATE_PCB_DRAFT) {
                if (templateId == null || templateId.isBlank())
                    return response("NEEDS_INPUT", "NEEDS_INPUT", "请选择已审核工程模板", null);
                var template =
                        catalog.list().stream()
                                .filter(t -> t.templateId().equals(templateId))
                                .findFirst()
                                .orElse(null);
                if (template == null)
                    return response("UNSUPPORTED", "TEMPLATE_UNAVAILABLE", "模板不存在或尚未审核", null);
                version = template.templateVersion();
                hash = template.templateSha256();
            }
            String key =
                    "chat:"
                            + com.nexus.ai.engineering.security.EngineeringSecrets.hash(
                                    context.requestId() + ":" + action.name());
            var command =
                    new EngineeringTaskCommand(
                            EngineeringDeviceService.PROTOCOL,
                            context.deviceId(),
                            action,
                            key,
                            context.sessionId(),
                            templateId,
                            version,
                            hash,
                            parent,
                            parameters == null ? Map.of() : parameters);
            var task =
                    TenantHelper.dynamic(
                            context.actor().tenantId(),
                            () -> tasks.create(context.actor(), command, context.requestId()));
            if (context.record(task))
                AiSseMessageHelper.sendEngineeringTask(context.actor().userId(), task);
            return response("ACCEPTED", null, "任务已派发，请查看真实任务状态与产物清单", task);
        } catch (EngineeringException error) {
            String status =
                    error.code().equals("NEEDS_INPUT")
                            ? "NEEDS_INPUT"
                            : error.code().equals("TEMPLATE_UNAVAILABLE")
                                    ? "UNSUPPORTED"
                                    : "REJECTED";
            return response(status, error.code(), error.getMessage(), null);
        }
    }

    private String response(
            String status,
            String error,
            String message,
            com.nexus.ai.engineering.domain.EngineeringTaskVo task) {
        var result = new LinkedHashMap<String, Object>();
        result.put("status", status);
        result.put("errorCode", error);
        result.put("message", message);
        if (task != null) {
            result.put("taskId", task.taskId());
            result.put("state", task.state());
            result.put("previewPath", task.previewPath());
            result.put("sessionId", task.sessionId());
            result.put("requestId", task.requestId());
            result.put("action", task.action());
        }
        return EngineeringJson.write(result);
    }
}
