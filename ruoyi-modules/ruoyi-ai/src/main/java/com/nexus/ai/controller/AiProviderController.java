package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.common.ai.domain.bo.AiProviderBo;
import com.nexus.common.ai.domain.vo.AiProviderVo;
import com.nexus.common.ai.service.IAiProviderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 厂商管理 Controller
 *
 * @author NexusERP
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/provider")
public class AiProviderController extends BaseController {

    private final IAiProviderService aiProviderService;

    /**
     * 分页查询AI厂商配置列表
     * <p>权限标识：{@code ai:provider:list}</p>
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return AI厂商配置分页列表
     */
    @SaCheckPermission("ai:provider:list")
    @GetMapping("/list")
    public TableDataInfo<AiProviderVo> list(AiProviderBo bo, PageQuery pageQuery) {
        return aiProviderService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出AI厂商配置数据到 Excel
     * <p>权限标识：{@code ai:provider:export}</p>
     *
     * @param bo       查询参数
     * @param response HTTP 响应对象
     */
    @SaCheckPermission("ai:provider:export")
    @Log(title = "AI厂商管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(AiProviderBo bo, HttpServletResponse response) {
        List<AiProviderVo> list = aiProviderService.selectList(bo);
        ExcelUtil.exportExcel(list, "AI厂商配置数据", AiProviderVo.class, response);
    }

    /**
     * 根据主键获取AI厂商配置详细信息
     * <p>权限标识：{@code ai:provider:query}</p>
     *
     * @param id AI厂商配置 ID
     * @return AI厂商配置详细信息
     */
    @SaCheckPermission("ai:provider:query")
    @GetMapping("/{id}")
    public R<AiProviderVo> getInfo(@PathVariable Long id) {
        return R.ok(aiProviderService.selectById(id));
    }

    /**
     * 新增AI厂商配置
     * <p>权限标识：{@code ai:provider:add}</p>
     *
     * @param bo AI厂商配置业务对象
     * @return 操作结果
     */
    @SaCheckPermission("ai:provider:add")
    @Log(title = "AI厂商管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody AiProviderBo bo) {
        return toAjax(aiProviderService.insertByBo(bo));
    }

    /**
     * 修改AI厂商配置
     * <p>权限标识：{@code ai:provider:edit}</p>
     *
     * @param bo AI厂商配置业务对象
     * @return 操作结果
     */
    @SaCheckPermission("ai:provider:edit")
    @Log(title = "AI厂商管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody AiProviderBo bo) {
        return toAjax(aiProviderService.updateByBo(bo));
    }

    /**
     * 批量删除AI厂商配置
     * <p>权限标识：{@code ai:provider:remove}</p>
     *
     * @param ids AI厂商配置 ID 数组
     * @return 操作结果
     */
    @SaCheckPermission("ai:provider:remove")
    @Log(title = "AI厂商管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(aiProviderService.deleteByIds(ids));
    }

}
