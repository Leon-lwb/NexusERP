package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.ai.domain.dto.AiModelTestRequest;
import com.nexus.ai.domain.dto.AiModelTestResult;
import com.nexus.ai.service.model.AiModelConnectionTestService;
import com.nexus.ai.service.model.AiModelDraftValidator;
import com.nexus.ai.service.model.AiProviderPolicyRegistry;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.ai.domain.bo.AiModelBo;
import com.nexus.common.ai.domain.vo.AiModelVo;
import com.nexus.common.ai.domain.vo.AiProviderVo;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.common.ai.service.IAiProviderService;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 模型管理 Controller
 *
 * @author NexusERP
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/model")
public class AiModelController extends BaseController {

    /** AI 模型业务服务，提供模型分页、详情、CRUD、默认值设置等。 */
    private final IAiModelService aiModelService;
    /** AI 厂商业务服务，新增/编辑/策略查询时按 providerId 解析厂商类型。 */
    private final IAiProviderService aiProviderService;
    /** 厂商协议策略注册表（协议分组可信事实源）。 */
    private final AiProviderPolicyRegistry aiProviderPolicyRegistry;
    /** 模型连接测试服务（草稿测试，不落库）。 */
    private final AiModelConnectionTestService aiModelConnectionTestService;

    /**
     * 分页查询模型列表
     * <p>权限标识：{@code ai:model:list}</p>
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 模型分页列表
     */
    @SaCheckPermission("ai:model:list")
    @GetMapping("/list")
    public TableDataInfo<AiModelVo> list(AiModelBo bo, PageQuery pageQuery) {
        return aiModelService.selectPageList(bo, pageQuery);
    }

    /**
     * 查询启用模型选项（供对话/文生图等终端用户按分类选择）
     * <p>不加 {@code @SaCheckPermission}——仅要求登录即可访问。返回的 {@link AiModelVo} 不含 apiKey，
     * 仅暴露 id/modelName/displayName/modelType/isDefault 等选择所需字段。</p>
     *
     * @param modelType                 模型类型（TEXT/VISION/AUDIO/EMBEDDING/RERANK/IMAGE，支持逗号分隔多类型如 TEXT,VISION），可选
     * @param requiredInputCapability   要求输入能力包含该值（字典 ai_model_capability，如 TEXT），可选
     * @param requiredOutputCapability  要求输出能力包含该值（字典 ai_model_capability，如 VECTOR），可选
     * @param status                    状态过滤（0启用 1停用），可选，默认只返回启用（status=0）
     * @return 启用状态的模型列表
     */
    @GetMapping("/options")
    public R<List<AiModelVo>> options(@RequestParam(required = false) String modelType,
                                      @RequestParam(required = false) String requiredInputCapability,
                                      @RequestParam(required = false) String requiredOutputCapability,
                                      @RequestParam(required = false) String status) {
        AiModelBo bo = new AiModelBo();
        bo.setModelType(modelType);
        bo.setRequiredInputCapability(requiredInputCapability);
        bo.setRequiredOutputCapability(requiredOutputCapability);
        // 默认仅启用模型（sys_normal_disable：0=正常/启用），允许显式指定其他状态
        bo.setStatus(StringUtils.isNotBlank(status) ? status : "0");
        return R.ok(aiModelService.selectList(bo));
    }

    /**
     * 导出模型数据到 Excel
     * <p>权限标识：{@code ai:model:export}</p>
     *
     * @param bo       查询参数
     * @param response HTTP 响应对象
     */
    @SaCheckPermission("ai:model:export")
    @Log(title = "AI模型管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(AiModelBo bo, HttpServletResponse response) {
        List<AiModelVo> list = aiModelService.selectList(bo);
        ExcelUtil.exportExcel(list, "AI模型数据", AiModelVo.class, response);
    }

    /**
     * 根据主键获取模型详细信息
     * <p>权限标识：{@code ai:model:query}</p>
     *
     * @param id 模型 ID
     * @return 模型详细信息
     */
    @SaCheckPermission("ai:model:query")
    @GetMapping("/{id}")
    public R<AiModelVo> getInfo(@PathVariable Long id) {
        return R.ok(aiModelService.selectById(id));
    }

    /**
     * 新增模型配置
     * <p>权限标识：{@code ai:model:add}</p>
     * <p>落库前按厂商类型执行协议/能力策略校验（草稿默认协议会补全回显）。</p>
     *
     * @param bo 模型业务对象
     * @return 操作结果
     */
    @SaCheckPermission("ai:model:add")
    @Log(title = "AI模型管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody AiModelBo bo) {
        validateProviderPolicy(bo);
        return toAjax(aiModelService.insertByBo(bo));
    }

    /**
     * 修改模型配置
     * <p>权限标识：{@code ai:model:edit}</p>
     * <p>落库前按 bo.providerId 对应厂商类型执行协议/能力策略校验（草稿默认协议会补全回显）。</p>
     *
     * @param bo 模型业务对象
     * @return 操作结果
     */
    @SaCheckPermission("ai:model:edit")
    @Log(title = "AI模型管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody AiModelBo bo) {
        validateProviderPolicy(bo);
        return toAjax(aiModelService.updateByBo(bo));
    }

    /**
     * 批量删除模型配置
     * <p>权限标识：{@code ai:model:remove}</p>
     *
     * @param ids 模型 ID 数组
     * @return 操作结果
     */
    @SaCheckPermission("ai:model:remove")
    @Log(title = "AI模型管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(aiModelService.deleteByIds(ids));
    }

    /**
     * 设置默认模型
     * <p>权限标识：{@code ai:model:edit}</p>
     *
     * @param id 模型 ID
     * @return 操作结果
     */
    @SaCheckPermission("ai:model:edit")
    @Log(title = "AI模型管理", businessType = BusinessType.UPDATE)
    @PutMapping("/setDefault/{id}")
    public R<Void> setDefault(@PathVariable Long id) {
        return toAjax(aiModelService.setDefaultModel(id));
    }

    /**
     * 模型草稿连接测试（不落库、不自动保存模型）
     * <p>权限标识：{@code ai:model:test}（菜单 4096）。</p>
     * <p>请求体只携带厂商 ID 与模型草稿参数，<b>不含 API Key</b>——密钥从 ai_provider 行内
     * 解密后仅在内存中使用。图片生成测试需 confirmBillableImageTest=true 二次确认。</p>
     *
     * @param request 测试请求
     * @return 测试结果（失败信息已脱敏）
     */
    @SaCheckPermission("ai:model:test")
    @Log(title = "AI模型连接测试", businessType = BusinessType.OTHER)
    @PostMapping("/test")
    public R<AiModelTestResult> test(@Validated @RequestBody AiModelTestRequest request) {
        return R.ok(aiModelConnectionTestService.test(request));
    }

    /**
     * 查询厂商的调用协议策略（默认协议 + 允许协议集合，供前端下拉约束）
     * <p>权限标识：{@code ai:model:query}</p>
     *
     * @param providerId 厂商 ID
     * @return 厂商协议策略
     * @throws ServiceException 厂商不存在，或其 provider_type 未注册策略
     *                          （厂商保存时不校验类型，脏数据在此给出用户可读提示）
     */
    @SaCheckPermission("ai:model:query")
    @GetMapping("/policy/{providerId}")
    public R<AiProviderPolicyRegistry.ProviderPolicy> policy(@PathVariable Long providerId) {
        AiProviderVo provider = aiProviderService.selectById(providerId);
        if (provider == null) {
            throw new ServiceException("厂商不存在或无权访问: " + providerId);
        }
        try {
            return R.ok(aiProviderPolicyRegistry.forProvider(provider.getProviderType()));
        } catch (IllegalArgumentException e) {
            // 注册表对非 Web 调用方保持 IllegalArgumentException 契约；
            // Web 边界转 ServiceException，避免全局处理器把它吞成"发生未知异常"
            throw new ServiceException(e.getMessage());
        }
    }

    /**
     * 落库前按厂商类型执行协议/能力策略校验
     * <p>校验器会补全草稿缺失的默认协议与 EMBEDDING/RERANK 锁定能力（写回 bo 随落库保存）。</p>
     *
     * @param bo 模型业务对象（providerId 必填）
     * @throws ServiceException 厂商不存在或草稿违反协议策略
     */
    private void validateProviderPolicy(AiModelBo bo) {
        AiProviderVo provider = aiProviderService.selectById(bo.getProviderId());
        if (provider == null) {
            throw new ServiceException("厂商不存在或无权访问: " + bo.getProviderId());
        }
        // 草稿校验器为 Task 4 纯 Java 类（无 Spring 注解），按策略注册表构建
        new AiModelDraftValidator(aiProviderPolicyRegistry)
            .resolveAndValidate(bo, provider.getProviderType());
    }
}
