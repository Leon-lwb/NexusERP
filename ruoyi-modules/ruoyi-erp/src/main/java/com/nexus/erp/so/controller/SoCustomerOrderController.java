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
import com.nexus.erp.so.domain.bo.SoCustomerOrderBo;
import com.nexus.erp.so.domain.vo.SoCustomerOrderVo;
import com.nexus.erp.so.service.ISoCustomerOrderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户订单 Controller
 * <p>
 * 提供客户订单的 RESTful API 接口，包括分页查询、详情查询、新增、编辑、删除及导出功能。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/so/customerOrder")
public class SoCustomerOrderController extends BaseController {

    private final ISoCustomerOrderService soCustomerOrderService;

    /**
     * 分页查询客户订单列表
     * <p>
     * 权限标识：{@code so:customerOrder:list}</p>
     *
     * @param bo        查询参数，包含客户名称、订单编号、日期范围等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 客户订单分页列表
     */
    @SaCheckPermission("so:customerOrder:list")
    @GetMapping("/list")
    public TableDataInfo<SoCustomerOrderVo> list(SoCustomerOrderBo bo, PageQuery pageQuery) {
        return soCustomerOrderService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出客户订单数据到 Excel
     * <p>权限标识：{@code so:customerOrder:export}</p>
     * <p>日志记录：title="客户订单"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("so:customerOrder:export")
    @Log(title = "客户订单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SoCustomerOrderBo bo, HttpServletResponse response) {
        List<SoCustomerOrderVo> list = soCustomerOrderService.selectList(bo);
        ExcelUtil.exportExcel(list, "客户订单数据", SoCustomerOrderVo.class, response);
    }

    /**
     * 根据主键获取客户订单详细信息
     * <p>
     * 权限标识：{@code so:customerOrder:query}</p>
     *
     * @param id 客户订单 ID
     * @return 客户订单详细信息
     */
    @SaCheckPermission("so:customerOrder:query")
    @GetMapping("/{id}")
    public R<SoCustomerOrderVo> getInfo(@PathVariable Long id) {
        return R.ok(soCustomerOrderService.selectById(id));
    }

    /**
     * 新增客户订单
     * <p>
     * 权限标识：{@code so:customerOrder:add}</p>
     * <p>日志记录：title="客户订单"，businessType=INSERT</p>
     *
     * @param bo 客户订单业务对象，包含客户信息、产品明细等
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("so:customerOrder:add")
    @Log(title = "客户订单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SoCustomerOrderBo bo) {
        return toAjax(soCustomerOrderService.insertByBo(bo));
    }

    /**
     * 修改客户订单
     * <p>
     * 权限标识：{@code so:customerOrder:edit}</p>
     * <p>日志记录：title="客户订单"，businessType=UPDATE</p>
     *
     * @param bo 客户订单业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("so:customerOrder:edit")
    @Log(title = "客户订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SoCustomerOrderBo bo) {
        return toAjax(soCustomerOrderService.updateByBo(bo));
    }

    /**
     * 批量删除客户订单
     * <p>
     * 权限标识：{@code so:customerOrder:remove}</p>
     * <p>日志记录：title="客户订单"，businessType=DELETE</p>
     *
     * @param ids 要删除的客户订单 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("so:customerOrder:remove")
    @Log(title = "客户订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(soCustomerOrderService.deleteByIds(ids));
    }
}
