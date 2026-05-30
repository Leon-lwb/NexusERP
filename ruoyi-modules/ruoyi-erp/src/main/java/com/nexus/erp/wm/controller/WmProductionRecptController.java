package com.nexus.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.wm.domain.bo.WmProductionRecptBo;
import com.nexus.erp.wm.domain.vo.WmProductionRecptVo;
import com.nexus.erp.wm.service.IWmProductionRecptService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/productionrecpt")
public class WmProductionRecptController extends BaseController {

    private final IWmProductionRecptService wmProductionRecptService;

    @SaCheckPermission("wm:productionrecpt:list")
    @GetMapping("/list")
    public TableDataInfo<WmProductionRecptVo> list(WmProductionRecptBo bo, PageQuery pageQuery) {
        return wmProductionRecptService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("wm:productionrecpt:export")
    @Log(title = "生产入库", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmProductionRecptBo bo, HttpServletResponse response) {
        List<WmProductionRecptVo> list = wmProductionRecptService.selectList(bo);
        ExcelUtil.exportExcel(list, "生产入库数据", WmProductionRecptVo.class, response);
    }

    @SaCheckPermission("wm:productionrecpt:query")
    @GetMapping("/{recptId}")
    public R<WmProductionRecptVo> getInfo(@PathVariable Long recptId) {
        return R.ok(wmProductionRecptService.selectById(recptId));
    }

    @SaCheckPermission("wm:productionrecpt:add")
    @Log(title = "生产入库", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmProductionRecptBo bo) {
        return toAjax(wmProductionRecptService.insertByBo(bo));
    }

    @SaCheckPermission("wm:productionrecpt:edit")
    @Log(title = "生产入库", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmProductionRecptBo bo) {
        return toAjax(wmProductionRecptService.updateByBo(bo));
    }

    @SaCheckPermission("wm:productionrecpt:remove")
    @Log(title = "生产入库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{recptIds}")
    public R<Void> remove(@PathVariable Long[] recptIds) {
        return toAjax(wmProductionRecptService.deleteByIds(recptIds));
    }

    @SaCheckPermission("wm:productionrecpt:edit")
    @Log(title = "生产入库", businessType = BusinessType.UPDATE)
    @PutMapping("/execute/{recptId}")
    public R<Void> execute(@PathVariable Long recptId) {
        wmProductionRecptService.execute(recptId);
        return R.ok();
    }
}
