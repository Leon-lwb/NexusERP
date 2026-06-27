package com.nexus.erp.pu.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;
import com.nexus.erp.pu.domain.enums.PurchaseOrderLineReceiveStatus;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 采购订单行 收货差异处理
 * <p>
 * 提供针对采购订单明细行收货差异(超收/短收)的后续处理端点，
 * 供业务人员确认差异并流转状态。
 * 所有接口均通过 Sa-Token 注解 {@link SaCheckPermission} 进行权限校验。
 *
 * @author nexus
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/pu/orderLine")
public class PuPurchaseOrderLineController {

    private final PuPurchaseOrderLineMapper orderLineMapper;

    /**
     * 标记超收行已确认
     * <p>
     * 将处于 OVER_RECEIVED 状态的订单行流转为 FULL_RECEIVED，
     * 表示业务人员已确认超收情况并关闭差异。
     * <p>权限标识：{@code pu:order:edit}</p>
     * <p>日志记录：title="采购订单行超收确认"，businessType=UPDATE</p>
     *
     * @param lineId 采购订单行 ID
     * @return 操作结果，成功返回 {@code R.ok()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("pu:order:edit")
    @Log(title = "采购订单行超收确认", businessType = BusinessType.UPDATE)
    @PutMapping("/{lineId}/confirmOverReceive")
    public R<Void> confirmOverReceive(@PathVariable Long lineId) {
        LambdaUpdateWrapper<PuPurchaseOrderLine> wrapper = Wrappers.<PuPurchaseOrderLine>lambdaUpdate()
            .eq(PuPurchaseOrderLine::getLineId, lineId)
            .eq(PuPurchaseOrderLine::getReceiveStatus, PurchaseOrderLineReceiveStatus.OVER_RECEIVED.getCode())
            .set(PuPurchaseOrderLine::getReceiveStatus, PurchaseOrderLineReceiveStatus.FULL_RECEIVED.getCode());
        int rows = orderLineMapper.update(null, wrapper);
        return rows > 0 ? R.ok() : R.fail("行不存在或非超收状态");
    }

    /**
     * 标记短收行为短收关闭(不再补货)
     * <p>
     * 将处于 SHORT_RECEIVED 状态的订单行流转为 FULL_RECEIVED，
     * 表示业务人员已确认短收并放弃后续补货。
     * <p>权限标识：{@code pu:order:edit}</p>
     * <p>日志记录：title="采购订单行短收关闭"，businessType=UPDATE</p>
     *
     * @param lineId 采购订单行 ID
     * @return 操作结果，成功返回 {@code R.ok()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("pu:order:edit")
    @Log(title = "采购订单行短收关闭", businessType = BusinessType.UPDATE)
    @PutMapping("/{lineId}/closeShortReceive")
    public R<Void> closeShortReceive(@PathVariable Long lineId) {
        LambdaUpdateWrapper<PuPurchaseOrderLine> wrapper = Wrappers.<PuPurchaseOrderLine>lambdaUpdate()
            .eq(PuPurchaseOrderLine::getLineId, lineId)
            .eq(PuPurchaseOrderLine::getReceiveStatus, PurchaseOrderLineReceiveStatus.SHORT_RECEIVED.getCode())
            .set(PuPurchaseOrderLine::getReceiveStatus, PurchaseOrderLineReceiveStatus.FULL_RECEIVED.getCode());
        int rows = orderLineMapper.update(null, wrapper);
        return rows > 0 ? R.ok() : R.fail("行不存在或非短收状态");
    }
}
