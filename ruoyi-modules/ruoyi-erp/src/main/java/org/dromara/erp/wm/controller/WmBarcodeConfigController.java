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
import org.dromara.erp.wm.domain.bo.WmBarcodeConfigBo;
import org.dromara.erp.wm.domain.vo.WmBarcodeConfigVo;
import org.dromara.erp.wm.service.IWmBarcodeConfigService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 条码配置 Controller
 *
 * 说明：
 * - 用于维护条码规则配置
 * - 条码生成逻辑从这里读取基础规则数据
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/barcode/config")
public class WmBarcodeConfigController extends BaseController {

    private final IWmBarcodeConfigService wmBarcodeConfigService;

    @SaCheckPermission("wm:barcodeConfig:list")
    @GetMapping("/list")
    public TableDataInfo<WmBarcodeConfigVo> list(WmBarcodeConfigBo bo, PageQuery pageQuery) {
        return wmBarcodeConfigService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("wm:barcodeConfig:export")
    @Log(title = "条码配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmBarcodeConfigBo bo, HttpServletResponse response) {
        List<WmBarcodeConfigVo> list = wmBarcodeConfigService.selectList(bo);
        ExcelUtil.exportExcel(list, "条码配置数据", WmBarcodeConfigVo.class, response);
    }

    @SaCheckPermission("wm:barcodeConfig:query")
    @GetMapping("/{id}")
    public R<WmBarcodeConfigVo> getInfo(@PathVariable Long id) {
        return R.ok(wmBarcodeConfigService.selectById(id));
    }

    @SaCheckPermission("wm:barcodeConfig:add")
    @Log(title = "条码配置", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmBarcodeConfigBo bo) {
        return toAjax(wmBarcodeConfigService.insertByBo(bo));
    }

    @SaCheckPermission("wm:barcodeConfig:edit")
    @Log(title = "条码配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmBarcodeConfigBo bo) {
        return toAjax(wmBarcodeConfigService.updateByBo(bo));
    }

    @SaCheckPermission("wm:barcodeConfig:remove")
    @Log(title = "条码配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmBarcodeConfigService.deleteByIds(ids));
    }
}
