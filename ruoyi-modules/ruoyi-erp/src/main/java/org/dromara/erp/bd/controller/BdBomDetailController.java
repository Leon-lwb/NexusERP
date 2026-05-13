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
import org.dromara.erp.bd.domain.bo.BdBomDetailBo;
import org.dromara.erp.bd.domain.vo.BdBomDetailVo;
import org.dromara.erp.bd.service.IBdBomDetailService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * BOM明细Controller
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/bom/detail")
public class BdBomDetailController extends BaseController {

    private final IBdBomDetailService bdBomDetailService;

    @SaCheckPermission("bd:bom:query")
    @GetMapping("/list")
    public TableDataInfo<BdBomDetailVo> list(BdBomDetailBo bo, PageQuery pageQuery) {
        return bdBomDetailService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("bd:bom:export")
    @Log(title = "BOM明细", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdBomDetailBo bo, HttpServletResponse response) {
        List<BdBomDetailVo> list = bdBomDetailService.selectList(bo);
        ExcelUtil.exportExcel(list, "BOM明细数据", BdBomDetailVo.class, response);
    }

    @SaCheckPermission("bd:bom:query")
    @GetMapping("/{id}")
    public R<BdBomDetailVo> getInfo(@PathVariable Long id) {
        return R.ok(bdBomDetailService.selectById(id));
    }

    @SaCheckPermission("bd:bom:add")
    @Log(title = "BOM明细", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdBomDetailBo bo) {
        return toAjax(bdBomDetailService.insertByBo(bo));
    }

    @SaCheckPermission("bd:bom:edit")
    @Log(title = "BOM明细", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdBomDetailBo bo) {
        return toAjax(bdBomDetailService.updateByBo(bo));
    }

    @SaCheckPermission("bd:bom:remove")
    @Log(title = "BOM明细", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bdBomDetailService.deleteByIds(ids));
    }
}
