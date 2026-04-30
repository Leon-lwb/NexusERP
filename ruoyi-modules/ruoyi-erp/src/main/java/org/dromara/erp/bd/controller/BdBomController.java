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
import org.dromara.erp.bd.domain.bo.BdBomBo;
import org.dromara.erp.bd.domain.vo.BdBomVo;
import org.dromara.erp.bd.service.IBdBomService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/bom")
public class BdBomController extends BaseController {

    private final IBdBomService bdBomService;

    @SaCheckPermission("bd:bom:list")
    @GetMapping("/list")
    public TableDataInfo<BdBomVo> list(BdBomBo bo, PageQuery pageQuery) {
        return bdBomService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("bd:bom:export")
    @Log(title = "BOM", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdBomBo bo, HttpServletResponse response) {
        List<BdBomVo> list = bdBomService.selectList(bo);
        ExcelUtil.exportExcel(list, "BOM数据", BdBomVo.class, response);
    }

    @SaCheckPermission("bd:bom:query")
    @GetMapping("/{id}")
    public R<BdBomVo> getInfo(@PathVariable Long id) {
        return R.ok(bdBomService.selectById(id));
    }

    @SaCheckPermission("bd:bom:add")
    @Log(title = "BOM", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdBomBo bo) {
        return toAjax(bdBomService.insertByBo(bo));
    }

    @SaCheckPermission("bd:bom:edit")
    @Log(title = "BOM", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdBomBo bo) {
        return toAjax(bdBomService.updateByBo(bo));
    }

    @SaCheckPermission("bd:bom:remove")
    @Log(title = "BOM", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bdBomService.deleteByIds(ids));
    }
}
