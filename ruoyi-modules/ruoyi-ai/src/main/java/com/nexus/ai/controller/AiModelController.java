package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.ai.domain.bo.AiModelBo;
import com.nexus.common.ai.domain.vo.AiModelVo;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.common.core.domain.R;
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

    private final IAiModelService aiModelService;

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
     *
     * @param bo 模型业务对象
     * @return 操作结果
     */
    @SaCheckPermission("ai:model:add")
    @Log(title = "AI模型管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody AiModelBo bo) {
        return toAjax(aiModelService.insertByBo(bo));
    }

    /**
     * 修改模型配置
     * <p>权限标识：{@code ai:model:edit}</p>
     *
     * @param bo 模型业务对象
     * @return 操作结果
     */
    @SaCheckPermission("ai:model:edit")
    @Log(title = "AI模型管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody AiModelBo bo) {
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
}
