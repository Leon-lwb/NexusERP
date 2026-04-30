package org.dromara.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.dromara.system.domain.SysDicArea;
import org.dromara.system.domain.vo.SysDicAreaVo;
import org.dromara.system.service.ISysDicAreaService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/area")
public class SysDicAreaController extends BaseController {

    private final ISysDicAreaService sysDicAreaService;

    @SaCheckPermission("system:area:list")
    @GetMapping("/list")
    public R<List<SysDicAreaVo>> list(SysDicArea sysDicArea) {
        return R.ok(sysDicAreaService.selectSysDicAreaTreeList(sysDicArea));
    }

    @SaCheckPermission("system:area:list")
    @GetMapping("/listChildren")
    public R<List<SysDicAreaVo>> listChildren(SysDicArea sysDicArea) {
        return R.ok(sysDicAreaService.selectChildrenTreeList(sysDicArea));
    }

    @SaCheckPermission("system:area:list")
    @GetMapping("/provinces")
    public R<List<SysDicAreaVo>> getProvinces() {
        return R.ok(sysDicAreaService.selectProvinces());
    }

    @SaCheckPermission("system:area:list")
    @GetMapping("/nextLevel/{parentAreaId}")
    public R<List<SysDicAreaVo>> getNextLevel(@PathVariable Long parentAreaId) {
        if (parentAreaId == null) {
            return R.fail("参数错误：parentAreaId不能为空");
        }
        return R.ok(sysDicAreaService.selectNextLevel(parentAreaId));
    }

    @SaCheckPermission("system:area:list")
    @GetMapping("/list/exclude/{pkid}")
    public R<List<SysDicAreaVo>> listAreaExcludeChild(@PathVariable(required = false) Long pkid) {
        return R.ok(sysDicAreaService.selectSysDicAreaTreeList(new SysDicArea()));
    }

    @SaCheckPermission("system:area:export")
    @Log(title = "行政区划", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysDicArea sysDicArea) {
        List<SysDicAreaVo> list = sysDicAreaService.selectSysDicAreaList(sysDicArea);
        ExcelUtil.exportExcel(list, "行政区划数据", SysDicAreaVo.class, response);
    }

    @SaCheckPermission("system:area:query")
    @GetMapping(value = "/{pkid}")
    public R<SysDicAreaVo> getInfo(@PathVariable("pkid") Long pkid) {
        return R.ok(sysDicAreaService.selectSysDicAreaByPkid(pkid));
    }

    @SaCheckPermission("system:area:add")
    @Log(title = "行政区划", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody SysDicArea sysDicArea) {
        return toAjax(sysDicAreaService.insertSysDicArea(sysDicArea));
    }

    @SaCheckPermission("system:area:edit")
    @Log(title = "行政区划", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody SysDicArea sysDicArea) {
        return toAjax(sysDicAreaService.updateSysDicArea(sysDicArea));
    }

    @SaCheckPermission("system:area:remove")
    @Log(title = "行政区划", businessType = BusinessType.DELETE)
    @DeleteMapping("/{pkids}")
    public R<Void> remove(@PathVariable Long[] pkids) {
        return toAjax(sysDicAreaService.deleteSysDicAreaByPkids(pkids));
    }
}
