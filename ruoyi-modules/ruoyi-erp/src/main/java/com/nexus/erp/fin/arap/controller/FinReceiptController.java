package com.nexus.erp.fin.arap.controller;

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
import com.nexus.erp.fin.arap.domain.bo.FinReceiptBo;
import com.nexus.erp.fin.arap.domain.vo.FinReceiptVo;
import com.nexus.erp.fin.arap.service.IFinReceiptService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 收付款单 Controller
 *
 * @author nexus
 * @date 2026-07-30
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/fin/arap/receipt")
public class FinReceiptController extends BaseController {

    private final IFinReceiptService finReceiptService;

    /** 分页查询收付款单 */
    @SaCheckPermission("fin:arap:receipt:list")
    @GetMapping("/list")
    public TableDataInfo<FinReceiptVo> list(FinReceiptBo bo, PageQuery pageQuery) {
        return finReceiptService.queryPageList(bo, pageQuery);
    }

    /** 导出收付款单 */
    @SaCheckPermission("fin:arap:receipt:export")
    @Log(title = "收付款单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(FinReceiptBo bo, HttpServletResponse response) {
        List<FinReceiptVo> list = finReceiptService.queryList(bo);
        ExcelUtil.exportExcel(list, "收付款单数据", FinReceiptVo.class, response);
    }

    /** 获取收付款单详情 */
    @SaCheckPermission("fin:arap:receipt:query")
    @GetMapping("/{receiptId}")
    public R<FinReceiptVo> getInfo(@PathVariable Long receiptId) {
        return R.ok(finReceiptService.queryById(receiptId));
    }

    /** 新增收付款单 */
    @SaCheckPermission("fin:arap:receipt:add")
    @Log(title = "收付款单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody FinReceiptBo bo) {
        return toAjax(finReceiptService.insertByBo(bo));
    }

    /** 修改收付款单 */
    @SaCheckPermission("fin:arap:receipt:edit")
    @Log(title = "收付款单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody FinReceiptBo bo) {
        return toAjax(finReceiptService.updateByBo(bo));
    }

    /** 删除收付款单 */
    @SaCheckPermission("fin:arap:receipt:remove")
    @Log(title = "收付款单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{receiptIds}")
    public R<Void> remove(@PathVariable Long[] receiptIds) {
        return toAjax(finReceiptService.deleteByIds(receiptIds));
    }

    /** 确认收付款单 */
    @SaCheckPermission("fin:arap:receipt:confirm")
    @Log(title = "收付款单", businessType = BusinessType.UPDATE)
    @PutMapping("/confirm/{receiptId}")
    public R<Void> confirm(@PathVariable Long receiptId) {
        finReceiptService.confirm(receiptId);
        return toAjax(1);
    }

    /** 取消收付款单 */
    @SaCheckPermission("fin:arap:receipt:cancel")
    @Log(title = "收付款单", businessType = BusinessType.UPDATE)
    @PutMapping("/cancel/{receiptId}")
    public R<Void> cancel(@PathVariable Long receiptId) {
        finReceiptService.cancel(receiptId);
        return toAjax(1);
    }
}
