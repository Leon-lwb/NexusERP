package com.nexus.erp.pu.controller;

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
import com.nexus.erp.pu.domain.bo.PuPurchaseReturnBo;
import com.nexus.erp.pu.domain.vo.PuPurchaseReturnVo;
import com.nexus.erp.pu.service.IPuPurchaseReturnService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 采购退货控制器
 * <p>
 * 提供采购退货单的 RESTful API 接口，包括：
 * <ul>
 *   <li>CRUD 操作：分页列表查询、详情查询、新增、编辑、删除</li>
 *   <li>数据导出（Excel）</li>
 *   <li>状态流转操作：确认、执行退货出库</li>
 *   <li>流程审批集成</li>
 * </ul>
 * 所有接口均通过 Sa-Token 注解 {@link SaCheckPermission} 进行权限校验。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/pu/return")
public class PuPurchaseReturnController extends BaseController {

    private final IPuPurchaseReturnService purchaseReturnService;

    /**
     * 分页查询采购退货单列表
     * <p>
     * 权限标识：{@code pu:return:list}</p>
     *
     * @param bo        查询参数，包含退货单编号、供应商、日期范围等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 采购退货单分页列表
     */
    @SaCheckPermission("pu:return:list")
    @GetMapping("/list")
    public TableDataInfo<PuPurchaseReturnVo> list(PuPurchaseReturnBo bo, PageQuery pageQuery) {
        return purchaseReturnService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出采购退货数据到 Excel
     * <p>权限标识：{@code pu:return:export}</p>
     * <p>日志记录：title="采购退货"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("pu:return:export")
    @Log(title = "采购退货", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PuPurchaseReturnBo bo, HttpServletResponse response) {
        List<PuPurchaseReturnVo> list = purchaseReturnService.selectList(bo);
        ExcelUtil.exportExcel(list, "采购退货数据", PuPurchaseReturnVo.class, response);
    }

    /**
     * 根据主键获取采购退货单详情（含明细行）
     * <p>
     * 权限标识：{@code pu:return:query}</p>
     *
     * @param returnId 采购退货单 ID
     * @return 采购退货单详细信息
     */
    @SaCheckPermission("pu:return:query")
    @GetMapping("/{returnId}")
    public R<PuPurchaseReturnVo> getInfo(@PathVariable Long returnId) {
        return R.ok(purchaseReturnService.selectById(returnId));
    }

    /**
     * 新增采购退货单（含明细行）
     * <p>
     * 权限标识：{@code pu:return:add}</p>
     * <p>日志记录：title="采购退货"，businessType=INSERT</p>
     *
     * @param bo 采购退货单业务对象，包含退货物料、数量等信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("pu:return:add")
    @Log(title = "采购退货", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody PuPurchaseReturnBo bo) {
        return toAjax(purchaseReturnService.insertByBo(bo));
    }

    /**
     * 修改采购退货单（含明细行）
     * <p>
     * 权限标识：{@code pu:return:edit}</p>
     * <p>日志记录：title="采购退货"，businessType=UPDATE</p>
     *
     * @param bo 采购退货单业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("pu:return:edit")
    @Log(title = "采购退货", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PuPurchaseReturnBo bo) {
        return toAjax(purchaseReturnService.updateByBo(bo));
    }

    /**
     * 确认退货单（草稿 → 已确认）
     * <p>权限标识：{@code pu:return:confirm}</p>
     * <p>日志记录：title="采购退货"，businessType=UPDATE</p>
     *
     * @param returnId 采购退货单 ID
     * @return 操作结果
     */
    @SaCheckPermission("pu:return:confirm")
    @Log(title = "采购退货", businessType = BusinessType.UPDATE)
    @PutMapping("/confirm/{returnId}")
    public R<Void> confirm(@PathVariable Long returnId) {
        purchaseReturnService.confirm(returnId);
        return R.ok();
    }

    /**
     * 执行退货出库（扣减库存并生成库存交易记录）
     * <p>权限标识：{@code pu:return:execute}</p>
     * <p>日志记录：title="采购退货"，businessType=UPDATE</p>
     *
     * @param returnId 采购退货单 ID
     * @return 操作结果
     */
    @SaCheckPermission("pu:return:execute")
    @Log(title = "采购退货", businessType = BusinessType.UPDATE)
    @PutMapping("/execute/{returnId}")
    public R<Void> execute(@PathVariable Long returnId) {
        purchaseReturnService.execute(returnId);
        return R.ok();
    }

    /**
     * 批量删除采购退货单
     * <p>
     * 权限标识：{@code pu:return:remove}</p>
     * <p>日志记录：title="采购退货"，businessType=DELETE</p>
     *
     * @param returnIds 要删除的采购退货单 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("pu:return:remove")
    @Log(title = "采购退货", businessType = BusinessType.DELETE)
    @DeleteMapping("/{returnIds}")
    public R<Void> remove(@PathVariable Long[] returnIds) {
        return toAjax(purchaseReturnService.deleteByIds(returnIds));
    }

    /**
     * 提交采购退货单审批
     * <p>权限标识：{@code pu:return:edit}</p>
     * <p>日志记录：title="采购退货"，businessType=UPDATE</p>
     *
     * @param returnId     采购退货单 ID
     * @param selectFlowId 选择的流程模板 ID
     * @return 操作结果
     */
    @SaCheckPermission("pu:return:edit")
    @Log(title = "采购退货", businessType = BusinessType.UPDATE)
    @PostMapping("/{returnId}/submit")
    public R<Void> submit(@PathVariable Long returnId, @RequestParam(required = false) String selectFlowId) {
        purchaseReturnService.submit(String.valueOf(returnId), selectFlowId);
        return R.ok();
    }

    /**
     * 获取采购退货单流程实例 ID
     * <p>
     * 权限标识：{@code pu:return:query}</p>
     *
     * @param returnId 采购退货单 ID
     * @return 流程实例 ID
     */
    @SaCheckPermission("pu:return:query")
    @GetMapping("/{returnId}/proc")
    public R<String> getProcInsId(@PathVariable Long returnId) {
        return R.ok(purchaseReturnService.getProcInsId(String.valueOf(returnId)));
    }
}
