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
import org.dromara.erp.pu.domain.bo.PuPurchaseRequisitionBo;
import org.dromara.erp.pu.domain.vo.PuPurchaseRequisitionVo;
import org.dromara.erp.pu.service.IPuPurchaseRequisitionService;
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
     */
    @SaCheckPermission("pu:requisition:list")
    @GetMapping("/list")
    public TableDataInfo<PuPurchaseRequisitionVo> list(PuPurchaseRequisitionBo bo, PageQuery pageQuery) {
        return purchaseRequisitionService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出采购申请数据到 Excel
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
     */
    @SaCheckPermission("pu:requisition:query")
    @GetMapping("/{requisitionId}")
    public R<PuPurchaseRequisitionVo> getInfo(@PathVariable Long requisitionId) {
        return R.ok(purchaseRequisitionService.selectById(requisitionId));
    }

    /**
     * 新增采购申请（含明细行）
     */
    @SaCheckPermission("pu:requisition:add")
    @Log(title = "采购申请", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody PuPurchaseRequisitionBo bo) {
        return toAjax(purchaseRequisitionService.insertByBo(bo));
    }

    /**
     * 修改采购申请（含明细行）
     */
    @SaCheckPermission("pu:requisition:edit")
    @Log(title = "采购申请", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PuPurchaseRequisitionBo bo) {
        return toAjax(purchaseRequisitionService.updateByBo(bo));
    }

    /**
     * 提交采购申请（草稿 → 已提交）
     */
    @SaCheckPermission("pu:requisition:submit")
    @Log(title = "采购申请", businessType = BusinessType.UPDATE)
    @PutMapping("/submit/{requisitionId}")
    public R<Void> submit(@PathVariable Long requisitionId) {
        purchaseRequisitionService.submit(requisitionId);
        return R.ok();
    }

    /**
     * 审批通过采购申请（已提交 → 已批准）
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
     *
     * @return 生成的采购订单ID
     */
    @SaCheckPermission("pu:requisition:convert")
    @Log(title = "采购申请", businessType = BusinessType.UPDATE)
    @PutMapping("/convert/{requisitionId}")
    public R<Long> convert(@PathVariable Long requisitionId) {
        return R.ok(purchaseRequisitionService.convertToPurchaseOrder(requisitionId));
    }

    /**
     * 关闭采购申请
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
     */
    @SaCheckPermission("pu:requisition:remove")
    @Log(title = "采购申请", businessType = BusinessType.DELETE)
    @DeleteMapping("/{requisitionIds}")
    public R<Void> remove(@PathVariable Long[] requisitionIds) {
        return toAjax(purchaseRequisitionService.deleteByIds(requisitionIds));
    }
}
