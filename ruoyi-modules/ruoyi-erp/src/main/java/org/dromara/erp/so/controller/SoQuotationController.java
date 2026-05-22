package org.dromara.erp.so.controller;

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
import org.dromara.erp.so.domain.bo.SoQuotationBo;
import org.dromara.erp.so.domain.vo.SoQuotationVo;
import org.dromara.erp.so.service.ISoQuotationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/so/quotation")
public class SoQuotationController extends BaseController {

    private final ISoQuotationService soQuotationService;

    @SaCheckPermission("so:quotation:list")
    @GetMapping("/list")
    public TableDataInfo<SoQuotationVo> list(SoQuotationBo bo, PageQuery pageQuery) {
        return soQuotationService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("so:quotation:export")
    @Log(title = "销售报价", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SoQuotationBo bo, HttpServletResponse response) {
        List<SoQuotationVo> list = soQuotationService.selectList(bo);
        ExcelUtil.exportExcel(list, "销售报价数据", SoQuotationVo.class, response);
    }

    @SaCheckPermission("so:quotation:query")
    @GetMapping("/{quotationId}")
    public R<SoQuotationVo> getInfo(@PathVariable Long quotationId) {
        return R.ok(soQuotationService.selectById(quotationId));
    }

    @SaCheckPermission("so:quotation:add")
    @Log(title = "销售报价", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SoQuotationBo bo) {
        return toAjax(soQuotationService.insertByBo(bo));
    }

    @SaCheckPermission("so:quotation:edit")
    @Log(title = "销售报价", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SoQuotationBo bo) {
        return toAjax(soQuotationService.updateByBo(bo));
    }

    @SaCheckPermission("so:quotation:remove")
    @Log(title = "销售报价", businessType = BusinessType.DELETE)
    @DeleteMapping("/{quotationIds}")
    public R<Void> remove(@PathVariable Long[] quotationIds) {
        return toAjax(soQuotationService.deleteByIds(quotationIds));
    }

    @SaCheckPermission("so:quotation:edit")
    @Log(title = "销售报价", businessType = BusinessType.UPDATE)
    @PutMapping("/confirm/{quotationId}")
    public R<Void> confirm(@PathVariable Long quotationId) {
        soQuotationService.confirm(quotationId);
        return R.ok();
    }

    @SaCheckPermission("so:quotation:edit")
    @Log(title = "销售报价", businessType = BusinessType.UPDATE)
    @PostMapping("/convertToOrder/{quotationId}")
    public R<Long> convertToOrder(@PathVariable Long quotationId) {
        Long orderId = soQuotationService.convertToSalesOrder(quotationId);
        return R.ok(orderId);
    }
}
