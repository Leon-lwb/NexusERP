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
import org.dromara.erp.pu.domain.bo.PuPurchaseOrderBo;
import org.dromara.erp.pu.domain.vo.PuPurchaseOrderVo;
import org.dromara.erp.pu.service.IPuPurchaseOrderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 采购订单控制器
 * <p>
 * 提供采购订单的 RESTful API 接口，包括：
 * <ul>
 *   <li>CRUD 操作：分页列表查询、详情查询、新增、编辑、删除</li>
 *   <li>数据导出（Excel）</li>
 *   <li>状态流转操作：提交、审批、关闭、作废</li>
 *   <li>联动操作：生成收货单据、从采购申请转单</li>
 * </ul>
 * 所有接口均通过 Sa-Token 注解 {@link SaCheckPermission} 进行权限校验。
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/pu/order")
public class PuPurchaseOrderController extends BaseController {

    private final IPuPurchaseOrderService purchaseOrderService;

    /**
     * 分页查询采购订单列表
     * <p>
     * 权限标识：{@code pu:order:list}</p>
     *
     * @param bo        查询参数，包含订单编号、供应商、日期范围等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 采购订单分页列表
     */
    @SaCheckPermission("pu:order:list")
    @GetMapping("/list")
    public TableDataInfo<PuPurchaseOrderVo> list(PuPurchaseOrderBo bo, PageQuery pageQuery) {
        return purchaseOrderService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出采购订单数据到 Excel
     * <p>权限标识：{@code pu:order:export}</p>
     * <p>日志记录：title="采购订单"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("pu:order:export")
    @Log(title = "采购订单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PuPurchaseOrderBo bo, HttpServletResponse response) {
        List<PuPurchaseOrderVo> list = purchaseOrderService.selectList(bo);
        ExcelUtil.exportExcel(list, "采购订单数据", PuPurchaseOrderVo.class, response);
    }

    /**
     * 根据主键获取采购订单详情（含明细行）
     * <p>
     * 权限标识：{@code pu:order:query}</p>
     *
     * @param orderId 采购订单 ID
     * @return 采购订单详细信息
     */
    @SaCheckPermission("pu:order:query")
    @GetMapping("/{orderId}")
    public R<PuPurchaseOrderVo> getInfo(@PathVariable Long orderId) {
        return R.ok(purchaseOrderService.selectById(orderId));
    }

    /**
     * 新增采购订单（含明细行）
     * <p>
     * 权限标识：{@code pu:order:add}</p>
     * <p>日志记录：title="采购订单"，businessType=INSERT</p>
     *
     * @param bo 采购订单业务对象，包含供应商信息、物料明细等
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("pu:order:add")
    @Log(title = "采购订单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody PuPurchaseOrderBo bo) {
        return toAjax(purchaseOrderService.insertByBo(bo));
    }

    /**
     * 修改采购订单（含明细行）
     * <p>
     * 权限标识：{@code pu:order:edit}</p>
     * <p>日志记录：title="采购订单"，businessType=UPDATE</p>
     *
     * @param bo 采购订单业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("pu:order:edit")
    @Log(title = "采购订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PuPurchaseOrderBo bo) {
        return toAjax(purchaseOrderService.updateByBo(bo));
    }

    /**
     * 提交采购订单（草稿 → 已提交，旧版无审批流）
     * <p>权限标识：{@code pu:order:submit}</p>
     * <p>日志记录：title="采购订单"，businessType=UPDATE</p>
     *
     * @param orderId 采购订单 ID
     * @return 操作结果
     */
    @SaCheckPermission("pu:order:submit")
    @Log(title = "采购订单", businessType = BusinessType.UPDATE)
    @PutMapping("/submit/{orderId}")
    public R<Void> submit(@PathVariable Long orderId) {
        purchaseOrderService.submit(orderId);
        return R.ok();
    }

    /**
     * 提交流程审批（启动 Flowable 审批流）
     * <p>权限标识：{@code pu:order:submit}</p>
     * <p>日志记录：title="采购订单"，businessType=UPDATE</p>
     *
     * @param orderId     采购订单 ID
     * @param selectFlowId 选择的流程模板 ID
     * @return 操作结果
     * @throws RuntimeException 提交审批失败时抛出运行时异常
     */
    @SaCheckPermission("pu:order:submit")
    @Log(title = "采购订单", businessType = BusinessType.UPDATE)
    @PostMapping("/{orderId}/submit")
    public R<Void> submitWithFlow(@PathVariable Long orderId,
                                   @RequestParam(value = "selectFlowId", required = false) String selectFlowId) {
        try {
            return toAjax(purchaseOrderService.submit(String.valueOf(orderId), selectFlowId));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 获取采购订单流程实例 ID
     * <p>
     * 权限标识：{@code pu:order:query}</p>
     *
     * @param orderId 采购订单 ID
     * @return 流程实例 ID
     */
    @SaCheckPermission("pu:order:query")
    @GetMapping("/{orderId}/proc")
    public R<String> getProcInsId(@PathVariable Long orderId) {
        return R.ok("查询成功", purchaseOrderService.getProcInsId(String.valueOf(orderId)));
    }

    /**
     * 审批通过采购订单（已提交 → 已批准）
     * <p>权限标识：{@code pu:order:approve}</p>
     * <p>日志记录：title="采购订单"，businessType=UPDATE</p>
     *
     * @param orderId 采购订单 ID
     * @return 操作结果
     */
    @SaCheckPermission("pu:order:approve")
    @Log(title = "采购订单", businessType = BusinessType.UPDATE)
    @PutMapping("/approve/{orderId}")
    public R<Void> approve(@PathVariable Long orderId) {
        purchaseOrderService.approve(orderId);
        return R.ok();
    }

    /**
     * 关闭采购订单
     * <p>权限标识：{@code pu:order:close}</p>
     * <p>日志记录：title="采购订单"，businessType=UPDATE</p>
     *
     * @param orderId 采购订单 ID
     * @return 操作结果
     */
    @SaCheckPermission("pu:order:close")
    @Log(title = "采购订单", businessType = BusinessType.UPDATE)
    @PutMapping("/close/{orderId}")
    public R<Void> close(@PathVariable Long orderId) {
        purchaseOrderService.close(orderId);
        return R.ok();
    }

    /**
     * 作废采购订单
     * <p>权限标识：{@code pu:order:cancel}</p>
     * <p>日志记录：title="采购订单"，businessType=UPDATE</p>
     *
     * @param orderId 采购订单 ID
     * @return 操作结果
     */
    @SaCheckPermission("pu:order:cancel")
    @Log(title = "采购订单", businessType = BusinessType.UPDATE)
    @PutMapping("/cancel/{orderId}")
    public R<Void> cancel(@PathVariable Long orderId) {
        purchaseOrderService.cancel(orderId);
        return R.ok();
    }

    /**
     * 根据采购订单生成收货联动单据（到货通知 + 物料收货单）
     * <p>权限标识：{@code pu:order:receive}</p>
     * <p>日志记录：title="采购订单"，businessType=UPDATE</p>
     *
     * @param orderId 采购订单 ID
     * @return 到货通知单 ID
     */
    @SaCheckPermission("pu:order:receive")
    @Log(title = "采购订单", businessType = BusinessType.UPDATE)
    @PostMapping("/receive/{orderId}")
    public R<Long> generateReceivingLink(@PathVariable Long orderId) {
        return R.ok(purchaseOrderService.generateReceivingLink(orderId));
    }

    /**
     * 根据采购申请生成采购订单草稿（用于前端界面预填）
     * <p>
     * 权限标识：{@code pu:order:query}</p>
     *
     * @param requisitionId 采购申请 ID
     * @return 采购订单业务对象（未持久化）
     */
    @SaCheckPermission("pu:order:query")
    @GetMapping("/generate/{requisitionId}")
    public R<PuPurchaseOrderBo> generateFromRequisition(@PathVariable Long requisitionId) {
        return R.ok(purchaseOrderService.generateFromRequisition(requisitionId));
    }

    /**
     * 批量删除采购订单
     * <p>
     * 权限标识：{@code pu:order:remove}</p>
     * <p>日志记录：title="采购订单"，businessType=DELETE</p>
     *
     * @param orderIds 要删除的采购订单 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("pu:order:remove")
    @Log(title = "采购订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{orderIds}")
    public R<Void> remove(@PathVariable Long[] orderIds) {
        return toAjax(purchaseOrderService.deleteByIds(orderIds));
    }
}
