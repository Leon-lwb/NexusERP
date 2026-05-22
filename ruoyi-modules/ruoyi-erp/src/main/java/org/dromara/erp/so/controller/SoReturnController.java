package org.dromara.erp.so.controller;

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
import org.dromara.erp.so.domain.bo.SoReturnBo;
import org.dromara.erp.so.domain.vo.SoReturnVo;
import org.dromara.erp.so.service.ISoReturnService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/so/return")
public class SoReturnController extends BaseController {

    private final ISoReturnService soReturnService;

    @SaCheckPermission("so:return:list")
    @GetMapping("/list")
    public TableDataInfo<SoReturnVo> list(SoReturnBo bo, PageQuery pageQuery) {
        return soReturnService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("so:return:export")
    @Log(title = "销售退货", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SoReturnBo bo, HttpServletResponse response) {
        List<SoReturnVo> list = soReturnService.selectList(bo);
        ExcelUtil.exportExcel(list, "销售退货数据", SoReturnVo.class, response);
    }

    @SaCheckPermission("so:return:query")
    @GetMapping("/{returnId}")
    public R<SoReturnVo> getInfo(@PathVariable Long returnId) {
        return R.ok(soReturnService.selectById(returnId));
    }

    @SaCheckPermission("so:return:add")
    @Log(title = "销售退货", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SoReturnBo bo) {
        return toAjax(soReturnService.insertByBo(bo));
    }

    @SaCheckPermission("so:return:edit")
    @Log(title = "销售退货", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SoReturnBo bo) {
        return toAjax(soReturnService.updateByBo(bo));
    }

    @SaCheckPermission("so:return:remove")
    @Log(title = "销售退货", businessType = BusinessType.DELETE)
    @DeleteMapping("/{returnIds}")
    public R<Void> remove(@PathVariable Long[] returnIds) {
        return toAjax(soReturnService.deleteByIds(returnIds));
    }

    @SaCheckPermission("so:return:edit")
    @Log(title = "销售退货", businessType = BusinessType.UPDATE)
    @PutMapping("/confirm/{returnId}")
    public R<Void> confirm(@PathVariable Long returnId) {
        soReturnService.confirm(returnId);
        return R.ok();
    }

    @SaCheckPermission("so:return:edit")
    @Log(title = "销售退货", businessType = BusinessType.UPDATE)
    @PutMapping("/execute/{returnId}")
    public R<Void> execute(@PathVariable Long returnId) {
        soReturnService.execute(returnId);
        return R.ok();
    }
}
