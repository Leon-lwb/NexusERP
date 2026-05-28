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
import com.nexus.erp.wm.domain.bo.WmPutawayRuleBo;
import com.nexus.erp.wm.domain.vo.WmPutawayRuleVo;
import com.nexus.erp.wm.service.IPutawayRuleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/putawayrule")
public class WmPutawayRuleController extends BaseController {

    private final IPutawayRuleService putawayRuleService;

    @SaCheckPermission("wm:putawayrule:list")
    @GetMapping("/list")
    public TableDataInfo<WmPutawayRuleVo> list(WmPutawayRuleBo bo, PageQuery pageQuery) {
        return putawayRuleService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("wm:putawayrule:export")
    @Log(title = "上架策略", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmPutawayRuleBo bo, HttpServletResponse response) {
        List<WmPutawayRuleVo> list = putawayRuleService.selectList(bo);
        ExcelUtil.exportExcel(list, "上架策略数据", WmPutawayRuleVo.class, response);
    }

    @SaCheckPermission("wm:putawayrule:query")
    @GetMapping("/{id}")
    public R<WmPutawayRuleVo> getInfo(@PathVariable Long id) {
        return R.ok(putawayRuleService.selectById(id));
    }

    @SaCheckPermission("wm:putawayrule:add")
    @Log(title = "上架策略", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmPutawayRuleBo bo) {
        return toAjax(putawayRuleService.insertByBo(bo));
    }

    @SaCheckPermission("wm:putawayrule:edit")
    @Log(title = "上架策略", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmPutawayRuleBo bo) {
        return toAjax(putawayRuleService.updateByBo(bo));
    }

    @SaCheckPermission("wm:putawayrule:remove")
    @Log(title = "上架策略", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(putawayRuleService.deleteByIds(ids));
    }
}
