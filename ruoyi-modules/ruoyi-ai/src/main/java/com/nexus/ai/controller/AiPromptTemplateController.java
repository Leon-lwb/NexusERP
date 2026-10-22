package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.ai.service.prompt.PromptTemplateService;
import com.nexus.common.ai.domain.bo.AiPromptTemplateBo;
import com.nexus.common.ai.domain.vo.AiPromptTemplateVo;
import com.nexus.common.ai.service.IAiPromptTemplateService;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;

import lombok.RequiredArgsConstructor;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI Prompt 模板管理 Controller
 * <p>提供 Prompt 模板的 CRUD 接口</p>
 *
 * @author NexusERP
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/prompt")
public class AiPromptTemplateController extends BaseController {

    /** Prompt 模板业务服务，提供模板 CRUD 与分页查询。 */
    private final IAiPromptTemplateService promptTemplateService;
    /** Prompt 模板渲染服务，负责模板变量渲染与缓存清理。 */
    private final PromptTemplateService promptTemplateRenderingService;

    /**
     * 查询 Prompt 模板列表
     */
    @SaCheckPermission("ai:prompt:list")
    @GetMapping("/list")
    public TableDataInfo<AiPromptTemplateVo> list(AiPromptTemplateBo bo, PageQuery pageQuery) {
        return promptTemplateService.selectPageList(bo, pageQuery);
    }

    /**
     * 查询 Prompt 模板列表（不分页）
     */
    @SaCheckPermission("ai:prompt:list")
    @GetMapping("/listAll")
    public R<List<AiPromptTemplateVo>> listAll(AiPromptTemplateBo bo) {
        return R.ok(promptTemplateService.selectList(bo));
    }

    /**
     * 获取 Prompt 模板详情
     */
    @SaCheckPermission("ai:prompt:query")
    @GetMapping("/{id}")
    public R<AiPromptTemplateVo> getInfo(@PathVariable Long id) {
        return R.ok(promptTemplateService.selectById(id));
    }

    /**
     * 新增 Prompt 模板
     * <p>新增后清除渲染缓存，确保 Agent 使用最新模板</p>
     */
    @SaCheckPermission("ai:prompt:add")
    @PostMapping
    public R<Void> add(@Validated @RequestBody AiPromptTemplateBo bo) {
        int result = promptTemplateService.insertByBo(bo);
        if (result > 0 && bo.getTemplateKey() != null) {
            promptTemplateRenderingService.evictCache(bo.getTemplateKey());
        }
        return toAjax(result);
    }

    /**
     * 修改 Prompt 模板
     * <p>修改后清除渲染缓存，确保 Agent 使用最新模板</p>
     */
    @SaCheckPermission("ai:prompt:edit")
    @PutMapping
    public R<Void> edit(@Validated @RequestBody AiPromptTemplateBo bo) {
        int result = promptTemplateService.updateByBo(bo);
        if (result > 0 && bo.getTemplateKey() != null) {
            promptTemplateRenderingService.evictCache(bo.getTemplateKey());
        }
        return toAjax(result);
    }

    /**
     * 删除 Prompt 模板
     * <p>删除后清除全部渲染缓存（批量删除可能涉及多个模板 key）</p>
     */
    @SaCheckPermission("ai:prompt:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        int result = promptTemplateService.deleteByIds(ids);
        if (result > 0) {
            promptTemplateRenderingService.evictAllCache();
        }
        return toAjax(result);
    }

    /**
     * 预览 Prompt 模板渲染结果
     * <p>使用当前系统数据渲染模板，返回实际注入 Agent 的文本内容</p>
     * <p>对于已知模板（erp_query_agent/erp_report_agent/supervisor）使用专用渲染方法，</p>
     * <p>其他模板使用空变量渲染原始模板内容</p>
     *
     * @param templateKey 模板标识
     * @return 渲染后的完整文本
     */
    @SaCheckPermission("ai:prompt:query")
    @GetMapping("/preview/{templateKey}")
    public R<String> preview(@PathVariable String templateKey) {
        String rendered;
        switch (templateKey) {
            case "erp_query_agent" -> rendered = promptTemplateRenderingService.renderQueryAgentContext();
            case "erp_report_agent" -> rendered = promptTemplateRenderingService.renderReportAgentContext();
            case "supervisor" -> rendered = promptTemplateRenderingService.renderSupervisorContext();
            default -> rendered = promptTemplateRenderingService.render(templateKey, Map.of());
        }
        // 使用 R.ok(String msg, T data) 重载，避免 R.ok(String) 把渲染结果误塞进 msg
        return R.ok("查询成功", rendered);
    }
}
