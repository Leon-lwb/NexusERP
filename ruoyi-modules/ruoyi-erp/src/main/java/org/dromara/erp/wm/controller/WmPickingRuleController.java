package org.dromara.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.erp.wm.domain.bo.WmPickingRuleBo;
import org.dromara.erp.wm.domain.vo.WmPickingRuleVo;
import org.dromara.erp.wm.service.IPickingRuleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/pickingrule")
public class WmPickingRuleController extends BaseController {

    private final IPickingRuleService pickingRuleService;

    @SaCheckPermission("wm:pickingrule:list")
    @GetMapping("/list")
    public TableDataInfo<WmPickingRuleVo> list(WmPickingRuleBo bo, PageQuery pageQuery) {
        return pickingRuleService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("wm:pickingrule:export")
    @Log(title = "拣货策略", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmPickingRuleBo bo, HttpServletResponse response) {
        List<WmPickingRuleVo> list = pickingRuleService.selectList(bo);
        ExcelUtil.exportExcel(list, "拣货策略数据", WmPickingRuleVo.class, response);
    }

    @SaCheckPermission("wm:pickingrule:query")
    @GetMapping("/{id}")
    public R<WmPickingRuleVo> getInfo(@PathVariable Long id) {
        return R.ok(pickingRuleService.selectById(id));
    }

    @SaCheckPermission("wm:pickingrule:add")
    @Log(title = "拣货策略", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmPickingRuleBo bo) {
        return toAjax(pickingRuleService.insertByBo(bo));
    }

    @SaCheckPermission("wm:pickingrule:edit")
    @Log(title = "拣货策略", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmPickingRuleBo bo) {
        return toAjax(pickingRuleService.updateByBo(bo));
    }

    @SaCheckPermission("wm:pickingrule:remove")
    @Log(title = "拣货策略", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(pickingRuleService.deleteByIds(ids));
    }
}
