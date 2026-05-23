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
import org.dromara.erp.wm.domain.bo.WmBarcodeBo;
import org.dromara.erp.wm.domain.vo.WmBarcodeVo;
import org.dromara.erp.wm.service.IWmBarcodeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

/**
 * 条码实例 Controller
 *
 * 说明：
 * - 用于查看/维护系统生成的条码实例
 * - 也提供一个轻量生成接口，便于先联调业务流程
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/barcode")
public class WmBarcodeController extends BaseController {

    private final IWmBarcodeService wmBarcodeService;

    @SaCheckPermission("wm:barcode:list")
    @GetMapping("/list")
    public TableDataInfo<WmBarcodeVo> list(WmBarcodeBo bo, PageQuery pageQuery) {
        return wmBarcodeService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("wm:barcode:export")
    @Log(title = "条码实例", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmBarcodeBo bo, HttpServletResponse response) {
        List<WmBarcodeVo> list = wmBarcodeService.selectList(bo);
        ExcelUtil.exportExcel(list, "条码实例数据", WmBarcodeVo.class, response);
    }

    @SaCheckPermission("wm:barcode:query")
    @GetMapping("/{id}")
    public R<WmBarcodeVo> getInfo(@PathVariable Long id) {
        return R.ok(wmBarcodeService.selectById(id));
    }

    @SaCheckPermission("wm:barcode:add")
    @Log(title = "条码实例", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmBarcodeBo bo) {
        return toAjax(wmBarcodeService.insertByBo(bo));
    }

    @SaCheckPermission("wm:barcode:edit")
    @Log(title = "条码实例", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmBarcodeBo bo) {
        return toAjax(wmBarcodeService.updateByBo(bo));
    }

    @SaCheckPermission("wm:barcode:remove")
    @Log(title = "条码实例", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmBarcodeService.deleteByIds(ids));
    }

    /**
     * 轻量生成条码接口
     *
     * 说明：
     * - 当前先使用 configId 作为规则入口
     * - 后续可以再扩展为按业务单据/物料类别自动选择配置
     */
    @SaCheckPermission("wm:barcode:add")
    @PostMapping("/generate")
    public R<String> generate(@RequestParam Long configId,
                              @RequestParam(required = false) String bizType,
                              @RequestParam(required = false) Long bizId,
                              @RequestParam(required = false) Long bizLineId,
                              @RequestParam(required = false) Long materialId,
                              @RequestParam(required = false) Long batchId,
                              @RequestParam(required = false) Long stockId) {
        return R.ok(wmBarcodeService.generateAndSave(configId, bizType, bizId, bizLineId, materialId, batchId, stockId, new Date()));
    }

    /**
     * 按配置编码生成条码。
     *
     * 说明：
     * - 这个接口更适合前端直接按规则编码发起调用
     * - 后续业务单据联动时，也可以复用这个接口
     */
    @SaCheckPermission("wm:barcode:add")
    @PostMapping("/generateByCode")
    public R<String> generateByCode(@RequestParam String configCode,
                                    @RequestParam(required = false) String bizType,
                                    @RequestParam(required = false) Long bizId,
                                    @RequestParam(required = false) Long bizLineId,
                                    @RequestParam(required = false) Long materialId,
                                    @RequestParam(required = false) Long batchId,
                                    @RequestParam(required = false) Long stockId) {
        return R.ok(wmBarcodeService.generateAndSaveByCode(configCode, bizType, bizId, bizLineId, materialId, batchId, stockId, new Date()));
    }
}
