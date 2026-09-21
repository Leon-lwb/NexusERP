package com.nexus.erp.wm.controller;

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
import com.nexus.erp.wm.domain.bo.WmPickingRuleBo;
import com.nexus.erp.wm.domain.vo.WmPickingRuleVo;
import com.nexus.erp.wm.service.IPickingRuleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
/**
 * 拣货规则管理 Controller
 * <p>
 * 提供拣货规则相关的 REST 接口，包括分页查询、新增、修改、删除、导入导出等操作。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/pickingrule")
public class WmPickingRuleController extends BaseController {

    /** 拣货规则业务服务，提供拣货规则相关能力 */
    private final IPickingRuleService pickingRuleService;

    /**
     * 分页查询拣货规则列表
     *
     * @param bo 拣货规则业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @SaCheckPermission("wm:pickingRule:list")
    @GetMapping("/list")
    public TableDataInfo<WmPickingRuleVo> list(WmPickingRuleBo bo, PageQuery pageQuery) {
        return pickingRuleService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出拣货规则列表
     *
     * @param bo 拣货规则业务对象（BO）
     * @param response 参数 response
     * @return 结果
     */
    @SaCheckPermission("wm:pickingRule:export")
    @Log(title = "拣货策略", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmPickingRuleBo bo, HttpServletResponse response) {
        List<WmPickingRuleVo> list = pickingRuleService.selectList(bo);
        ExcelUtil.exportExcel(list, "拣货策略数据", WmPickingRuleVo.class, response);
    }

    /**
     * 获取拣货规则详情
     *
     * @param id 主键 ID
     * @return 结果对象
     */
    @SaCheckPermission("wm:pickingRule:query")
    @GetMapping("/{id}")
    public R<WmPickingRuleVo> getInfo(@PathVariable Long id) {
        return R.ok(pickingRuleService.selectById(id));
    }

    /**
     * 新增拣货规则
     *
     * @param bo 拣货规则业务对象（BO）
     * @return 结果对象
     */
    @SaCheckPermission("wm:pickingRule:add")
    @Log(title = "拣货策略", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmPickingRuleBo bo) {
        return toAjax(pickingRuleService.insertByBo(bo));
    }

    /**
     * 修改拣货规则
     *
     * @param bo 拣货规则业务对象（BO）
     * @return 结果对象
     */
    @SaCheckPermission("wm:pickingRule:edit")
    @Log(title = "拣货策略", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmPickingRuleBo bo) {
        return toAjax(pickingRuleService.updateByBo(bo));
    }

    /**
     * 删除拣货规则
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 结果对象
     */
    @SaCheckPermission("wm:pickingRule:remove")
    @Log(title = "拣货策略", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(pickingRuleService.deleteByIds(ids));
    }
}
