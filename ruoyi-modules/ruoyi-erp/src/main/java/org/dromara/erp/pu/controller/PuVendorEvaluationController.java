package org.dromara.erp.pu.controller;

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
import org.dromara.erp.pu.domain.bo.PuVendorEvaluationBo;
import org.dromara.erp.pu.domain.vo.PuVendorEvaluationVo;
import org.dromara.erp.pu.service.IPuVendorEvaluationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/pu/vendor-evaluation")
public class PuVendorEvaluationController extends BaseController {

    private final IPuVendorEvaluationService vendorEvaluationService;

    @SaCheckPermission("pu:vendorEvaluation:list")
    @GetMapping("/list")
    public TableDataInfo<PuVendorEvaluationVo> list(PuVendorEvaluationBo bo, PageQuery pageQuery) {
        return vendorEvaluationService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("pu:vendorEvaluation:export")
    @Log(title = "供应商评估", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PuVendorEvaluationBo bo, HttpServletResponse response) {
        List<PuVendorEvaluationVo> list = vendorEvaluationService.selectList(bo);
        ExcelUtil.exportExcel(list, "供应商评估数据", PuVendorEvaluationVo.class, response);
    }

    @SaCheckPermission("pu:vendorEvaluation:query")
    @GetMapping("/{evaluationId}")
    public R<PuVendorEvaluationVo> getInfo(@PathVariable Long evaluationId) {
        return R.ok(vendorEvaluationService.selectById(evaluationId));
    }

    @SaCheckPermission("pu:vendorEvaluation:add")
    @Log(title = "供应商评估", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody PuVendorEvaluationBo bo) {
        return toAjax(vendorEvaluationService.insertByBo(bo));
    }

    @SaCheckPermission("pu:vendorEvaluation:edit")
    @Log(title = "供应商评估", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PuVendorEvaluationBo bo) {
        return toAjax(vendorEvaluationService.updateByBo(bo));
    }

    @SaCheckPermission("pu:vendorEvaluation:remove")
    @Log(title = "供应商评估", businessType = BusinessType.DELETE)
    @DeleteMapping("/{evaluationIds}")
    public R<Void> remove(@PathVariable Long[] evaluationIds) {
        return toAjax(vendorEvaluationService.deleteByIds(evaluationIds));
    }

    @SaCheckPermission("pu:vendorEvaluation:edit")
    @Log(title = "供应商评估-计算", businessType = BusinessType.UPDATE)
    @PostMapping("/calculate/{vendorId}")
    public R<Void> calculate(@PathVariable Long vendorId) {
        vendorEvaluationService.calculateByVendor(vendorId);
        return R.ok();
    }

    @SaCheckPermission("pu:vendorEvaluation:edit")
    @Log(title = "供应商评估-批量计算", businessType = BusinessType.UPDATE)
    @PostMapping("/calculate-all")
    public R<Void> calculateAll() {
        vendorEvaluationService.batchCalculateAll();
        return R.ok();
    }
}
