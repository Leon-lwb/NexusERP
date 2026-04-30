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
import org.dromara.erp.sch.domain.bo.SchHolidayBo;
import org.dromara.erp.sch.domain.vo.SchHolidayVo;
import org.dromara.erp.sch.service.ISchHolidayService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 节假日设置Controller
 *
 * @author nexus
 * @date 2025-12-17
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/sch/holiday")
public class SchHolidayController extends BaseController {

    private final ISchHolidayService schHolidayService;

    @SaCheckPermission("sch:holiday:list")
    @GetMapping("/list")
    public TableDataInfo<SchHolidayVo> list(SchHolidayBo bo, PageQuery pageQuery) {
        return schHolidayService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("sch:holiday:export")
    @Log(title = "节假日设置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchHolidayBo bo, HttpServletResponse response) {
        List<SchHolidayVo> list = schHolidayService.selectList(bo);
        ExcelUtil.exportExcel(list, "节假日设置数据", SchHolidayVo.class, response);
    }

    @SaCheckPermission("sch:holiday:query")
    @GetMapping("/{holidayId}")
    public R<SchHolidayVo> getInfo(@PathVariable Long holidayId) {
        return R.ok(schHolidayService.selectById(holidayId));
    }

    @SaCheckPermission("sch:holiday:add")
    @Log(title = "节假日设置", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchHolidayBo bo) {
        return toAjax(schHolidayService.insertByBo(bo));
    }

    @SaCheckPermission("sch:holiday:edit")
    @Log(title = "节假日设置", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchHolidayBo bo) {
        return toAjax(schHolidayService.updateByBo(bo));
    }

    @SaCheckPermission("sch:holiday:remove")
    @Log(title = "节假日设置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{holidayIds}")
    public R<Void> remove(@PathVariable Long[] holidayIds) {
        return toAjax(schHolidayService.deleteByIds(holidayIds));
    }
}
