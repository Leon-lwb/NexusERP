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
import org.dromara.erp.bd.domain.bo.BdWorkstationBo;
import org.dromara.erp.bd.domain.vo.BdWorkstationVo;
import org.dromara.erp.bd.service.IBdWorkstationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/workstation")
public class BdWorkstationController extends BaseController {

    private final IBdWorkstationService bdWorkstationService;

    @SaCheckPermission("bd:workstation:list")
    @GetMapping("/list")
    public TableDataInfo<BdWorkstationVo> list(BdWorkstationBo bo, PageQuery pageQuery) {
        return bdWorkstationService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("bd:workstation:export")
    @Log(title = "工作站", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdWorkstationBo bo, HttpServletResponse response) {
        List<BdWorkstationVo> list = bdWorkstationService.selectList(bo);
        ExcelUtil.exportExcel(list, "工作站数据", BdWorkstationVo.class, response);
    }

    @SaCheckPermission("bd:workstation:query")
    @GetMapping("/{id}")
    public R<BdWorkstationVo> getInfo(@PathVariable Long id) {
        return R.ok(bdWorkstationService.selectById(id));
    }

    @SaCheckPermission("bd:workstation:add")
    @Log(title = "工作站", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdWorkstationBo bo) {
        return toAjax(bdWorkstationService.insertByBo(bo));
    }

    @SaCheckPermission("bd:workstation:edit")
    @Log(title = "工作站", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdWorkstationBo bo) {
        return toAjax(bdWorkstationService.updateByBo(bo));
    }

    @SaCheckPermission("bd:workstation:remove")
    @Log(title = "工作站", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bdWorkstationService.deleteByIds(ids));
    }
}
