package com.nexus.erp.so.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.so.domain.bo.SoQuotationBo;
import com.nexus.erp.so.domain.vo.SoQuotationVo;
import com.nexus.erp.so.service.ISoQuotationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 销售报价Controller
 * <p>
 * 提供销售报价单的 RESTful API 接口，包括 CRUD 操作、数据导出、报价确认及转销售订单等功能。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/so/quotation")
public class SoQuotationController extends BaseController {

    private final ISoQuotationService soQuotationService;

    /**
     * 分页查询销售报价列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 销售报价分页列表
     */
    @SaCheckPermission("so:quotation:list")
    @GetMapping("/list")
    public TableDataInfo<SoQuotationVo> list(SoQuotationBo bo, PageQuery pageQuery) {
        return soQuotationService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出销售报价数据到 Excel
     * <p>权限标识：{@code so:quotation:export}</p>
     * <p>日志记录：title="销售报价"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象
     */
    @SaCheckPermission("so:quotation:export")
    @Log(title = "销售报价", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SoQuotationBo bo, HttpServletResponse response) {
        List<SoQuotationVo> list = soQuotationService.selectList(bo);
        ExcelUtil.exportExcel(list, "销售报价数据", SoQuotationVo.class, response);
    }

    /**
     * 获取销售报价详细信息
     * <p>权限标识：{@code so:quotation:query}</p>
     *
     * @param quotationId 销售报价ID
     * @return 销售报价详细信息
     */
    @SaCheckPermission("so:quotation:query")
    @GetMapping("/{quotationId}")
    public R<SoQuotationVo> getInfo(@PathVariable Long quotationId) {
        return R.ok(soQuotationService.selectById(quotationId));
    }

    /**
     * 新增销售报价
     * <p>权限标识：{@code so:quotation:add}</p>
     * <p>日志记录：title="销售报价"，businessType=INSERT</p>
     *
     * @param bo 销售报价业务对象
     * @return 操作结果
     */
    @SaCheckPermission("so:quotation:add")
    @Log(title = "销售报价", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SoQuotationBo bo) {
        return toAjax(soQuotationService.insertByBo(bo));
    }

    /**
     * 修改销售报价
     * <p>权限标识：{@code so:quotation:edit}</p>
     * <p>日志记录：title="销售报价"，businessType=UPDATE</p>
     *
     * @param bo 销售报价业务对象
     * @return 操作结果
     */
    @SaCheckPermission("so:quotation:edit")
    @Log(title = "销售报价", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SoQuotationBo bo) {
        return toAjax(soQuotationService.updateByBo(bo));
    }

    /**
     * 删除销售报价
     * <p>权限标识：{@code so:quotation:remove}</p>
     * <p>日志记录：title="销售报价"，businessType=DELETE</p>
     *
     * @param quotationIds 销售报价ID数组
     * @return 操作结果
     */
    @SaCheckPermission("so:quotation:remove")
    @Log(title = "销售报价", businessType = BusinessType.DELETE)
    @DeleteMapping("/{quotationIds}")
    public R<Void> remove(@PathVariable Long[] quotationIds) {
        return toAjax(soQuotationService.deleteByIds(quotationIds));
    }

    /**
     * 确认销售报价
     * <p>权限标识：{@code so:quotation:edit}</p>
     * <p>日志记录：title="销售报价"，businessType=UPDATE</p>
     *
     * @param quotationId 销售报价ID
     * @return 操作结果
     */
    @SaCheckPermission("so:quotation:edit")
    @Log(title = "销售报价", businessType = BusinessType.UPDATE)
    @PutMapping("/confirm/{quotationId}")
    public R<Void> confirm(@PathVariable Long quotationId) {
        soQuotationService.confirm(quotationId);
        return R.ok();
    }

    /**
     * 将销售报价转换为销售订单（仅「已确认」可转；加防重复提交）
     * <p>权限标识：{@code so:quotation:edit}</p>
     * <p>日志记录：title="销售报价"，businessType=UPDATE</p>
     *
     * @param quotationId 销售报价ID
     * @return 生成的销售订单ID
     */
    @RepeatSubmit
    @SaCheckPermission("so:quotation:edit")
    @Log(title = "销售报价", businessType = BusinessType.UPDATE)
    @PostMapping("/convertToOrder/{quotationId}")
    public R<Long> convertToOrder(@PathVariable Long quotationId) {
        Long orderId = soQuotationService.convertToSalesOrder(quotationId);
        return R.ok(orderId);
    }
}
