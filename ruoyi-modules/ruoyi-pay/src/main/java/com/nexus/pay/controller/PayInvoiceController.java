package com.nexus.pay.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.pay.domain.PayInvoice;
import com.nexus.pay.domain.bo.PayInvoiceBo;
import com.nexus.pay.domain.vo.PayInvoiceVo;
import com.nexus.pay.service.IPayInvoiceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 发票Controller
 *
 * @author geek
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/pay/invoice")
@Tag(name = "【发票】管理")
public class PayInvoiceController extends BaseController {

    private final IPayInvoiceService payInvoiceService;

    @Operation(summary = "查询发票列表")
    @SaCheckPermission("pay:invoice:list")
    @GetMapping("/list")
    public TableDataInfo<PayInvoiceVo> list(PayInvoiceBo bo, PageQuery pageQuery) {
        return payInvoiceService.selectPageList(bo, pageQuery);
    }

    @Operation(summary = "导出发票列表")
    @SaCheckPermission("pay:invoice:export")
    @Log(title = "发票", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, PayInvoiceBo bo) {
        List<PayInvoiceVo> list = payInvoiceService.selectList(bo);
        ExcelUtil.exportExcel(list, "发票数据", PayInvoiceVo.class, response);
    }

    @Operation(summary = "获取发票详细信息")
    @SaCheckPermission("pay:invoice:query")
    @GetMapping(value = "/{invoiceId}")
    public R<PayInvoiceVo> getInfo(@PathVariable Long invoiceId) {
        return R.ok(payInvoiceService.selectById(invoiceId));
    }

    @Operation(summary = "新增发票")
    @SaCheckPermission("pay:invoice:add")
    @Log(title = "发票", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody PayInvoiceBo bo) {
        PayInvoice entity = new PayInvoice();
        entity.setOrderNumber(bo.getOrderNumber());
        entity.setInvoiceType(bo.getInvoiceType());
        entity.setInvoiceHeader(bo.getInvoiceHeader());
        entity.setInvoiceNumber(bo.getInvoiceNumber());
        entity.setInvoicePhone(bo.getInvoicePhone());
        entity.setInvoiceEmail(bo.getInvoiceEmail());
        entity.setInvoiceRemark(bo.getInvoiceRemark());
        return toAjax(payInvoiceService.insertPayInvoice(entity));
    }

    @Operation(summary = "修改发票")
    @SaCheckPermission("pay:invoice:edit")
    @Log(title = "发票", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PayInvoiceBo bo) {
        PayInvoice entity = new PayInvoice();
        entity.setInvoiceId(bo.getInvoiceId());
        entity.setOrderNumber(bo.getOrderNumber());
        entity.setInvoiceType(bo.getInvoiceType());
        entity.setInvoiceHeader(bo.getInvoiceHeader());
        entity.setInvoiceNumber(bo.getInvoiceNumber());
        entity.setInvoicePhone(bo.getInvoicePhone());
        entity.setInvoiceEmail(bo.getInvoiceEmail());
        entity.setInvoiceRemark(bo.getInvoiceRemark());
        return toAjax(payInvoiceService.updatePayInvoice(entity));
    }

    @Operation(summary = "删除发票")
    @SaCheckPermission("pay:invoice:remove")
    @Log(title = "发票", businessType = BusinessType.DELETE)
    @DeleteMapping("/{invoiceIds}")
    public R<Void> remove(@PathVariable Long[] invoiceIds) {
        return toAjax(payInvoiceService.deleteByIds(invoiceIds));
    }

}
