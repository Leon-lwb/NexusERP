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
import com.nexus.erp.pu.domain.bo.PuPurchaseRequisitionBo;
import com.nexus.erp.pu.domain.vo.PuPurchaseRequisitionVo;
import com.nexus.erp.pu.service.IPuPurchaseRequisitionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 采购申请控制器
 * <p>
 * 提供采购申请的 RESTful API 接口，包括：
 * <ul>
 *   <li>CRUD 操作：分页列表查询、详情查询、新增、编辑、删除</li>
 *   <li>数据导出（Excel）</li>
 *   <li>状态流转操作：提交、审批、关闭、作废</li>
 *   <li>转单操作：将已批准的采购申请转换为采购订单</li>
 * </ul>
 * 所有接口均通过 Sa-Token 注解 {@link SaCheckPermission} 进行权限校验。
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/pu/requisition")
public class PuPurchaseRequisitionController extends BaseController {

    private final IPuPurchaseRequisitionService purchaseRequisitionService;

    /**
     * 分页查询采购申请列表
     * <p>
     * 权限标识：{@code pu:requisition:list}</p>
     *
     * @param bo        查询参数，包含申请编号、申请人、日期范围等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 采购申请分页列表
     */
    @SaCheckPermission("pu:requisition:list")
    @GetMapping("/list")
    public TableDataInfo<PuPurchaseRequisitionVo> list(PuPurchaseRequisitionBo bo, PageQuery pageQuery) {
        return purchaseRequisitionService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出采购申请数据到 Excel
     * <p>权限标识：{@code pu:requisition:export}</p>
     * <p>日志记录：title="采购申请"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("pu:requisition:export")
    @Log(title = "采购申请", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PuPurchaseRequisitionBo bo, HttpServletResponse response) {
        List<PuPurchaseRequisitionVo> list = purchaseRequisitionService.selectList(bo);
        ExcelUtil.exportExcel(list, "采购申请数据", PuPurchaseRequisitionVo.class, response);
    }

    /**
     * 根据主键获取采购申请详情（含明细行）
     * <p>
     * 权限标识：{@code pu:requisition:query}</p>
     *
     * @param requisitionId 采购申请 ID
     * @return 采购申请详细信息
     */
    @SaCheckPermission("pu:requisition:query")
    @GetMapping("/{requisitionId}")
    public R<PuPurchaseRequisitionVo> getInfo(@PathVariable Long requisitionId) {
        return R.ok(purchaseRequisitionService.selectById(requisitionId));
    }

    /**
     * 新增采购申请（含明细行）
     * <p>
     * 权限标识：{@code pu:requisition:add}</p>
     * <p>日志记录：title="采购申请"，businessType=INSERT</p>
     *
     * @param bo 采购申请业务对象，包含物料信息、需求数量等
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("pu:requisition:add")
    @Log(title = "采购申请", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody PuPurchaseRequisitionBo bo) {
        return toAjax(purchaseRequisitionService.insertByBo(bo));
    }

    /**
     * 修改采购申请（含明细行）
     * <p>
     * 权限标识：{@code pu:requisition:edit}</p>
     * <p>日志记录：title="采购申请"，businessType=UPDATE</p>
     *
     * @param bo 采购申请业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("pu:requisition:edit")
    @Log(title = "采购申请", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PuPurchaseRequisitionBo bo) {
        return toAjax(purchaseRequisitionService.updateByBo(bo));
    }

    /**
     * 提交采购申请（草稿 → 已提交，旧版无审批流）
     * <p>权限标识：{@code pu:requisition:submit}</p>
     * <p>日志记录：title="采购申请"，businessType=UPDATE</p>
     *
     * @param requisitionId 采购申请 ID
     * @return 操作结果
     */
    @SaCheckPermission("pu:requisition:submit")
    @Log(title = "采购申请", businessType = BusinessType.UPDATE)
    @PutMapping("/submit/{requisitionId}")
    public R<Void> submit(@PathVariable Long requisitionId) {
        purchaseRequisitionService.submit(requisitionId);
        return R.ok();
    }

    /**
     * 提交流程审批（启动 Flowable 审批流）
     * <p>权限标识：{@code pu:requisition:submit}</p>
     * <p>日志记录：title="采购申请"，businessType=UPDATE</p>
     *
     * @param requisitionId 采购申请 ID
     * @param selectFlowId  选择的流程模板 ID
     * @return 操作结果
     * @throws RuntimeException 提交审批失败时抛出运行时异常
     */
    @SaCheckPermission("pu:requisition:submit")
    @Log(title = "采购申请", businessType = BusinessType.UPDATE)
    @PostMapping("/{requisitionId}/submit")
    public R<Void> submitWithFlow(@PathVariable Long requisitionId,
                                   @RequestParam(value = "selectFlowId", required = false) String selectFlowId) {
        try {
            return toAjax(purchaseRequisitionService.submit(String.valueOf(requisitionId), selectFlowId));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 获取采购申请流程实例 ID
     * <p>
     * 权限标识：{@code pu:requisition:query}</p>
     *
     * @param requisitionId 采购申请 ID
     * @return 流程实例 ID
     */
    @SaCheckPermission("pu:requisition:query")
    @GetMapping("/{requisitionId}/proc")
    public R<String> getProcInsId(@PathVariable Long requisitionId) {
        return R.ok("查询成功", purchaseRequisitionService.getProcInsId(String.valueOf(requisitionId)));
    }

    /**
     * 审批通过采购申请（已提交 → 已批准）
     * <p>权限标识：{@code pu:requisition:approve}</p>
     * <p>日志记录：title="采购申请"，businessType=UPDATE</p>
     *
     * @param requisitionId 采购申请 ID
     * @return 操作结果
     */
    @SaCheckPermission("pu:requisition:approve")
    @Log(title = "采购申请", businessType = BusinessType.UPDATE)
    @PutMapping("/approve/{requisitionId}")
    public R<Void> approve(@PathVariable Long requisitionId) {
        purchaseRequisitionService.approve(requisitionId);
        return R.ok();
    }

    /**
     * 将已批准的采购申请转换为采购订单
     * <p>权限标识：{@code pu:requisition:convert}</p>
     * <p>日志记录：title="采购申请"，businessType=UPDATE</p>
     *
     * @param requisitionId 采购申请 ID
     * @return 生成的采购订单 ID
     */
    @SaCheckPermission("pu:requisition:convert")
    @Log(title = "采购申请", businessType = BusinessType.UPDATE)
    @PutMapping("/convert/{requisitionId}")
    public R<Long> convert(@PathVariable Long requisitionId) {
        return R.ok(purchaseRequisitionService.convertToPurchaseOrder(requisitionId));
    }

    /**
     * 关闭采购申请
     * <p>权限标识：{@code pu:requisition:close}</p>
     * <p>日志记录：title="采购申请"，businessType=UPDATE</p>
     *
     * @param requisitionId 采购申请 ID
     * @return 操作结果
     */
    @SaCheckPermission("pu:requisition:close")
    @Log(title = "采购申请", businessType = BusinessType.UPDATE)
    @PutMapping("/close/{requisitionId}")
    public R<Void> close(@PathVariable Long requisitionId) {
        purchaseRequisitionService.close(requisitionId);
        return R.ok();
    }

    /**
     * 作废采购申请
     * <p>权限标识：{@code pu:requisition:cancel}</p>
     * <p>日志记录：title="采购申请"，businessType=UPDATE</p>
     *
     * @param requisitionId 采购申请 ID
     * @return 操作结果
     */
    @SaCheckPermission("pu:requisition:cancel")
    @Log(title = "采购申请", businessType = BusinessType.UPDATE)
    @PutMapping("/cancel/{requisitionId}")
    public R<Void> cancel(@PathVariable Long requisitionId) {
        purchaseRequisitionService.cancel(requisitionId);
        return R.ok();
    }

    /**
     * 批量删除采购申请
     * <p>
     * 权限标识：{@code pu:requisition:remove}</p>
     * <p>日志记录：title="采购申请"，businessType=DELETE</p>
     *
     * @param requisitionIds 要删除的采购申请 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("pu:requisition:remove")
    @Log(title = "采购申请", businessType = BusinessType.DELETE)
    @DeleteMapping("/{requisitionIds}")
    public R<Void> remove(@PathVariable Long[] requisitionIds) {
        return toAjax(purchaseRequisitionService.deleteByIds(requisitionIds));
    }

    /**
     * 从低库存自动生成采购申请
     * <p>
     * 扫描所有物料，当库存低于安全库存时自动生成采购申请。</p>
     * <p>权限标识：{@code pu:requisition:add}</p>
     * <p>日志记录：title="采购申请-低库存生成"，businessType=INSERT</p>
     *
     * @return 生成的采购申请ID列表
     */
    @SaCheckPermission("pu:requisition:add")
    @Log(title = "采购申请-低库存生成", businessType = BusinessType.INSERT)
    @PostMapping("/generate-from-low-stock")
    public R<List<Long>> generateFromLowStock() {
        return R.ok(purchaseRequisitionService.generateFromLowStock());
    }
}
