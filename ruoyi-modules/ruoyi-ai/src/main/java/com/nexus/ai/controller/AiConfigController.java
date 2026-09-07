package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.ai.service.config.IAiConfigService;
import com.nexus.common.ai.domain.bo.AiConfigBo;
import com.nexus.common.ai.domain.vo.AiConfigVo;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.web.core.BaseController;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 运行参数配置 Controller
 * <p>nexus.ai 运行参数（SQL 安全/向量存储/联网搜索/流程编排/知识图谱）的页面化管理，
 * 配置落 ai_config 表，修改后回填 AiProperties 即时生效并广播多节点</p>
 *
 * @author NexusERP
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/config")
public class AiConfigController extends BaseController {

    /** AI 运行参数配置业务服务 */
    private final IAiConfigService aiConfigService;

    /**
     * 读取当前生效的 AI 运行参数
     * <p>权限标识：{@code ai:config:list}；apiKey 明文不回显</p>
     */
    @SaCheckPermission("ai:config:list")
    @GetMapping
    public R<AiConfigVo> getConfig() {
        return R.ok(aiConfigService.getConfig());
    }

    /**
     * 保存 AI 运行参数并即时生效
     * <p>权限标识：{@code ai:config:edit}；webSearch.apiKey 留空表示保留原值；
     * 向量库连接/表前缀变更自动联动缓存失效，并广播集群其他节点重载</p>
     */
    @SaCheckPermission("ai:config:edit")
    @Log(title = "AI参数设置", businessType = BusinessType.UPDATE, excludeParamNames = {"apiKey"})
    @PutMapping
    public R<Void> updateConfig(@Validated @RequestBody AiConfigBo bo) {
        aiConfigService.updateConfig(bo);
        return R.ok();
    }
}
