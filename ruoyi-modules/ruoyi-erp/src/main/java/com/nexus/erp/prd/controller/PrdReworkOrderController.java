package com.nexus.erp.prd.controller;

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
import com.nexus.erp.prd.domain.bo.PrdReworkOrderBo;
import com.nexus.erp.prd.domain.vo.PrdReworkOrderVo;
import com.nexus.erp.prd.service.IPrdReworkOrderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 返工订单 Controller
 * <p>
 * 提供返工订单的 RESTful API 接口，包括：
 * <ul>
 *   <li>CRUD 操作：分页列表查询、详情查询、新增、编辑、删除</li>
 *   <li>数据导出（Excel）</li>
 *   <li>状态流转操作：开始、完工</li>
 * </ul>
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/prd/rework")
public class PrdReworkOrderController extends BaseController {

    private final IPrdReworkOrderService reworkOrderService;

    /**
     * 分页查询返工订单列表
     * <p>
     * 权限标识：{@code prd:rework:list}</p>
     *
     * @param bo        查询参数，包含返工单号、来源工单、物料等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 返工订单分页列表
     */
    @SaCheckPermission("prd:rework:list")
    @GetMapping("/list")
    public TableDataInfo<PrdReworkOrderVo> list(PrdReworkOrderBo bo, PageQuery pageQuery) {
        return reworkOrderService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出返工订单数据到 Excel
     * <p>权限标识：{@code prd:rework:export}</p>
     * <p>日志记录：title="返工订单"，businessType=EXPORT</p>
     *
     * @param bo       查询参数
     * @param response HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("prd:rework:export")
    @Log(title = "返工订单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PrdReworkOrderBo bo, HttpServletResponse response) {
        List<PrdReworkOrderVo> list = reworkOrderService.selectList(bo);
        ExcelUtil.exportExcel(list, "返工订单数据", PrdReworkOrderVo.class, response);
    }

    /**
     * 根据主键获取返工订单详细信息
     * <p>
     * 权限标识：{@code prd:rework:query}</p>
     *
     * @param reworkId 返工订单 ID
     * @return 返工订单详细信息
     */
    @SaCheckPermission("prd:rework:query")
    @GetMapping("/{reworkId}")
    public R<PrdReworkOrderVo> getInfo(@PathVariable Long reworkId) {
        return R.ok(reworkOrderService.selectById(reworkId));
    }

    /**
     * 新增返工订单
     * <p>
     * 权限标识：{@code prd:rework:add}</p>
     * <p>日志记录：title="返工订单"，businessType=INSERT</p>
     *
     * @param bo 返工订单业务对象，包含来源工单、物料、返工数量等信息
     * @return 新增后的返工订单详细信息
     */
    @SaCheckPermission("prd:rework:add")
    @Log(title = "返工订单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<PrdReworkOrderVo> add(@Validated @RequestBody PrdReworkOrderBo bo) {
        reworkOrderService.insertByBo(bo);
        return R.ok(reworkOrderService.selectById(bo.getReworkId()));
    }

    /**
     * 修改返工订单
     * <p>
     * 权限标识：{@code prd:rework:edit}</p>
     * <p>日志记录：title="返工订单"，businessType=UPDATE</p>
     *
     * @param bo 返工订单业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:rework:edit")
    @Log(title = "返工订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PrdReworkOrderBo bo) {
        return toAjax(reworkOrderService.updateByBo(bo));
    }

    /**
     * 批量删除返工订单
     * <p>
     * 权限标识：{@code prd:rework:remove}</p>
     * <p>日志记录：title="返工订单"，businessType=DELETE</p>
     *
     * @param reworkIds 要删除的返工订单 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:rework:remove")
    @Log(title = "返工订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{reworkIds}")
    public R<Void> remove(@PathVariable Long[] reworkIds) {
        return toAjax(reworkOrderService.deleteByIds(reworkIds));
    }

    /**
     * 开始返工订单
     * <p>
     * 将返工订单状态更新为进行中。
     * 权限标识：{@code prd:rework:edit}</p>
     * <p>日志记录：title="返工订单-开始"，businessType=UPDATE</p>
     *
     * @param reworkId 返工订单 ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:rework:edit")
    @Log(title = "返工订单-开始", businessType = BusinessType.UPDATE)
    @PutMapping("/start/{reworkId}")
    public R<Void> start(@PathVariable Long reworkId) {
        reworkOrderService.start(reworkId);
        return R.ok();
    }

    /**
     * 完工返工订单
     * <p>
     * 将返工订单状态更新为已完成，并将报废数量回写到来源工单。
     * 权限标识：{@code prd:rework:edit}</p>
     * <p>日志记录：title="返工订单-完工"，businessType=UPDATE</p>
     *
     * @param reworkId 返工订单 ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:rework:edit")
    @Log(title = "返工订单-完工", businessType = BusinessType.UPDATE)
    @PutMapping("/complete/{reworkId}")
    public R<Void> complete(@PathVariable Long reworkId) {
        reworkOrderService.complete(reworkId);
        return R.ok();
    }
}
