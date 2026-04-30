package org.dromara.erp.bd.controller;

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
import org.dromara.erp.bd.domain.bo.BdUnitOfMeasureBo;
import org.dromara.erp.bd.domain.vo.BdUnitOfMeasureVo;
import org.dromara.erp.bd.service.IBdUnitOfMeasureService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/measure")
public class BdUnitOfMeasureController extends BaseController {

    private final IBdUnitOfMeasureService bdUnitOfMeasureService;

    @SaCheckPermission("bd:measure:list")
    @GetMapping("/list")
    public TableDataInfo<BdUnitOfMeasureVo> list(BdUnitOfMeasureBo bo, PageQuery pageQuery) {
        return bdUnitOfMeasureService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("bd:measure:list")
    @GetMapping("/all")
    public R<List<BdUnitOfMeasureVo>> all() {
        return R.ok(bdUnitOfMeasureService.selectList(new BdUnitOfMeasureBo()));
    }

    @SaCheckPermission("bd:measure:export")
    @Log(title = "计量单位", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdUnitOfMeasureBo bo, HttpServletResponse response) {
        List<BdUnitOfMeasureVo> list = bdUnitOfMeasureService.selectList(bo);
        ExcelUtil.exportExcel(list, "计量单位数据", BdUnitOfMeasureVo.class, response);
    }

    @SaCheckPermission("bd:measure:query")
    @GetMapping("/{uomId}")
    public R<BdUnitOfMeasureVo> getInfo(@PathVariable Long uomId) {
        return R.ok(bdUnitOfMeasureService.selectById(uomId));
    }

    @SaCheckPermission("bd:measure:add")
    @Log(title = "计量单位", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdUnitOfMeasureBo bo) {
        return toAjax(bdUnitOfMeasureService.insertByBo(bo));
    }

    @SaCheckPermission("bd:measure:edit")
    @Log(title = "计量单位", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdUnitOfMeasureBo bo) {
        return toAjax(bdUnitOfMeasureService.updateByBo(bo));
    }

    @SaCheckPermission("bd:measure:remove")
    @Log(title = "计量单位", businessType = BusinessType.DELETE)
    @DeleteMapping("/{uomIds}")
    public R<Void> remove(@PathVariable Long[] uomIds) {
        return toAjax(bdUnitOfMeasureService.deleteByIds(uomIds));
    }
}
