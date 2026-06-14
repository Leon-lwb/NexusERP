package com.nexus.erp.so.controller;

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
import com.nexus.erp.so.domain.bo.SoInvoiceBo;
import com.nexus.erp.so.domain.vo.SoInvoiceVo;
import com.nexus.erp.so.service.ISoInvoiceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 销售发票 Controller
 * <p>
 * 管理销售发票信息，包含发票基本信息、明细行及状态流转。
 * </p>
 *
 * @author nexus
 * @date 2026-06-14
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/so/invoice")
public class SoInvoiceController extends BaseController {

    private final ISoInvoiceService soInvoiceService;

    /**
     * 分页查询销售发票列表
     * <p>权限标识：{@code so:invoice:list}</p>
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 销售发票分页列表
     */
    @SaCheckPermission("so:invoice:list")
    @GetMapping("/list")
    public TableDataInfo<SoInvoiceVo> list(SoInvoiceBo bo, PageQuery pageQuery) {
        return soInvoiceService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出销售发票数据到 Excel
     * <p>权限标识：{@code so:invoice:export}</p>
     *
     * @param bo       查询参数
     * @param response HTTP 响应对象
     */
    @SaCheckPermission("so:invoice:export")
    @Log(title = "销售发票", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SoInvoiceBo bo, HttpServletResponse response) {
        List<SoInvoiceVo> list = soInvoiceService.selectList(bo);
        ExcelUtil.exportExcel(list, "销售发票数据", SoInvoiceVo.class, response);
    }

    /**
     * 根据主键获取销售发票详细信息
     * <p>权限标识：{@code so:invoice:query}</p>
     *
     * @param id 发票ID
     * @return 销售发票详细信息
     */
    @SaCheckPermission("so:invoice:query")
    @GetMapping("/{id}")
    public R<SoInvoiceVo> getInfo(@PathVariable Long id) {
        return R.ok(soInvoiceService.selectById(id));
    }

    /**
     * 新增销售发票
     * <p>权限标识：{@code so:invoice:add}</p>
     *
     * @param bo 销售发票业务对象
     * @return 操作结果
     */
    @SaCheckPermission("so:invoice:add")
    @Log(title = "销售发票", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SoInvoiceBo bo) {
        return toAjax(soInvoiceService.insertByBo(bo));
    }

    /**
     * 修改销售发票
     * <p>权限标识：{@code so:invoice:edit}</p>
     *
     * @param bo 销售发票业务对象
     * @return 操作结果
     */
    @SaCheckPermission("so:invoice:edit")
    @Log(title = "销售发票", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SoInvoiceBo bo) {
        return toAjax(soInvoiceService.updateByBo(bo));
    }

    /**
     * 批量删除销售发票
     * <p>权限标识：{@code so:invoice:remove}</p>
     *
     * @param ids 发票ID数组
     * @return 操作结果
     */
    @SaCheckPermission("so:invoice:remove")
    @Log(title = "销售发票", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(soInvoiceService.deleteByIds(ids));
    }
}
