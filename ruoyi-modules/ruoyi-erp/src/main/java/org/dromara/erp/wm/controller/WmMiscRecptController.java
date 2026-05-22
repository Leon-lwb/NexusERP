package org.dromara.erp.wm.controller;

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
import org.dromara.erp.wm.domain.bo.WmMiscRecptBo;
import org.dromara.erp.wm.domain.vo.WmMiscRecptVo;
import org.dromara.erp.wm.service.IWmMiscRecptService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/miscrecpt")
public class WmMiscRecptController extends BaseController {

    private final IWmMiscRecptService wmMiscRecptService;

    @SaCheckPermission("wm:miscrecpt:list")
    @GetMapping("/list")
    public TableDataInfo<WmMiscRecptVo> list(WmMiscRecptBo bo, PageQuery pageQuery) {
        return wmMiscRecptService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("wm:miscrecpt:export")
    @Log(title = "杂项入库", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmMiscRecptBo bo, HttpServletResponse response) {
        List<WmMiscRecptVo> list = wmMiscRecptService.selectList(bo);
        ExcelUtil.exportExcel(list, "杂项入库数据", WmMiscRecptVo.class, response);
    }

    @SaCheckPermission("wm:miscrecpt:query")
    @GetMapping("/{recptId}")
    public R<WmMiscRecptVo> getInfo(@PathVariable Long recptId) {
        return R.ok(wmMiscRecptService.selectById(recptId));
    }

    @SaCheckPermission("wm:miscrecpt:add")
    @Log(title = "杂项入库", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmMiscRecptBo bo) {
        return toAjax(wmMiscRecptService.insertByBo(bo));
    }

    @SaCheckPermission("wm:miscrecpt:edit")
    @Log(title = "杂项入库", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmMiscRecptBo bo) {
        return toAjax(wmMiscRecptService.updateByBo(bo));
    }

    @SaCheckPermission("wm:miscrecpt:remove")
    @Log(title = "杂项入库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{recptIds}")
    public R<Void> remove(@PathVariable Long[] recptIds) {
        return toAjax(wmMiscRecptService.deleteByIds(recptIds));
    }

    @SaCheckPermission("wm:miscrecpt:edit")
    @Log(title = "杂项入库", businessType = BusinessType.UPDATE)
    @PutMapping("/execute/{recptId}")
    public R<Void> execute(@PathVariable Long recptId) {
        wmMiscRecptService.execute(recptId);
        return R.ok();
    }
}