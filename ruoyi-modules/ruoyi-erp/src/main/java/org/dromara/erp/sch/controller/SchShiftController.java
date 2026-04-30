package org.dromara.erp.sch.controller;

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
import org.dromara.erp.sch.domain.bo.SchShiftBo;
import org.dromara.erp.sch.domain.vo.SchShiftVo;
import org.dromara.erp.sch.service.ISchShiftService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 班次定义Controller
 *
 * @author nexus
 * @date 2025-12-25
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/sch/shift")
public class SchShiftController extends BaseController {

    private final ISchShiftService schShiftService;

    @SaCheckPermission("sch:shift:list")
    @GetMapping("/list")
    public TableDataInfo<SchShiftVo> list(SchShiftBo bo, PageQuery pageQuery) {
        return schShiftService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("sch:shift:export")
    @Log(title = "班次定义", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchShiftBo bo, HttpServletResponse response) {
        List<SchShiftVo> list = schShiftService.selectList(bo);
        ExcelUtil.exportExcel(list, "班次定义数据", SchShiftVo.class, response);
    }

    @SaCheckPermission("sch:shift:query")
    @GetMapping("/{shiftId}")
    public R<SchShiftVo> getInfo(@PathVariable Long shiftId) {
        return R.ok(schShiftService.selectById(shiftId));
    }

    @SaCheckPermission("sch:shift:add")
    @Log(title = "班次定义", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchShiftBo bo) {
        return toAjax(schShiftService.insertByBo(bo));
    }

    @SaCheckPermission("sch:shift:edit")
    @Log(title = "班次定义", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchShiftBo bo) {
        return toAjax(schShiftService.updateByBo(bo));
    }

    @SaCheckPermission("sch:shift:remove")
    @Log(title = "班次定义", businessType = BusinessType.DELETE)
    @DeleteMapping("/{shiftIds}")
    public R<Void> remove(@PathVariable Long[] shiftIds) {
        return toAjax(schShiftService.deleteByIds(shiftIds));
    }
}
