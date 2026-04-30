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
import org.dromara.erp.so.domain.bo.SoCustomerOrderBo;
import org.dromara.erp.so.domain.vo.SoCustomerOrderVo;
import org.dromara.erp.so.service.ISoCustomerOrderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户订单Controller
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/so/customerOrder")
public class SoCustomerOrderController extends BaseController {

    private final ISoCustomerOrderService soCustomerOrderService;

    /**
     * 查询客户订单列表
     */
    @SaCheckPermission("so:customerOrder:list")
    @GetMapping("/list")
    public TableDataInfo<SoCustomerOrderVo> list(SoCustomerOrderBo bo, PageQuery pageQuery) {
        return soCustomerOrderService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出客户订单列表
     */
    @SaCheckPermission("so:customerOrder:export")
    @Log(title = "客户订单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SoCustomerOrderBo bo, HttpServletResponse response) {
        List<SoCustomerOrderVo> list = soCustomerOrderService.selectList(bo);
        ExcelUtil.exportExcel(list, "客户订单数据", SoCustomerOrderVo.class, response);
    }

    /**
     * 获取客户订单详细信息
     */
    @SaCheckPermission("so:customerOrder:query")
    @GetMapping("/{id}")
    public R<SoCustomerOrderVo> getInfo(@PathVariable Long id) {
        return R.ok(soCustomerOrderService.selectById(id));
    }

    /**
     * 新增客户订单
     */
    @SaCheckPermission("so:customerOrder:add")
    @Log(title = "客户订单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SoCustomerOrderBo bo) {
        return toAjax(soCustomerOrderService.insertByBo(bo));
    }

    /**
     * 修改客户订单
     */
    @SaCheckPermission("so:customerOrder:edit")
    @Log(title = "客户订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SoCustomerOrderBo bo) {
        return toAjax(soCustomerOrderService.updateByBo(bo));
    }

    /**
     * 删除客户订单
     */
    @SaCheckPermission("so:customerOrder:remove")
    @Log(title = "客户订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(soCustomerOrderService.deleteByIds(ids));
    }
}
