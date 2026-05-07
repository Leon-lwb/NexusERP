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
import org.dromara.erp.bd.domain.bo.BdWorkshopBo;
import org.dromara.erp.bd.domain.vo.BdWorkshopVo;
import org.dromara.erp.bd.service.IBdWorkshopService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 车间信息Controller
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/workshop")
public class BdWorkshopController extends BaseController {

    private final IBdWorkshopService bdWorkshopService;

    @SaCheckPermission("bd:workshop:list")
    @GetMapping("/list")
    public TableDataInfo<BdWorkshopVo> list(BdWorkshopBo bo, PageQuery pageQuery) {
        return bdWorkshopService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("bd:workshop:export")
    @Log(title = "车间", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdWorkshopBo bo, HttpServletResponse response) {
        List<BdWorkshopVo> list = bdWorkshopService.selectList(bo);
        ExcelUtil.exportExcel(list, "车间数据", BdWorkshopVo.class, response);
    }

    @SaCheckPermission("bd:workshop:query")
    @GetMapping("/{id}")
    public R<BdWorkshopVo> getInfo(@PathVariable Long id) {
        return R.ok(bdWorkshopService.selectById(id));
    }

    @SaCheckPermission("bd:workshop:add")
    @Log(title = "车间", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdWorkshopBo bo) {
        return toAjax(bdWorkshopService.insertByBo(bo));
    }

    @SaCheckPermission("bd:workshop:edit")
    @Log(title = "车间", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdWorkshopBo bo) {
        return toAjax(bdWorkshopService.updateByBo(bo));
    }

    @SaCheckPermission("bd:workshop:remove")
    @Log(title = "车间", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bdWorkshopService.deleteByIds(ids));
    }
}
