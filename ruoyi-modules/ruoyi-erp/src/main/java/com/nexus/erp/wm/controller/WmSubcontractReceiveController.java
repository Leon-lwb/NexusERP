package com.nexus.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.wm.domain.bo.WmSubcontractReceiveBo;
import com.nexus.erp.wm.service.IWmSubcontractReceiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 委外收货单 Controller (PCBA 委外闭环)
 * <p>
 * 收货单创建(默认半成品仓路由) -> 过账(自动建批次/精确库存行/actualQty 累加/达量自动完工)。
 * 权限复用委外订单权限点({@code prd:subcontract:query} / {@code prd:subcontract:receive})。</p>
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/sc-receive")
public class WmSubcontractReceiveController extends BaseController {

    private final IWmSubcontractReceiveService receiveService;

    /**
     * 按委外订单查询收货单列表
     * <p>权限标识：{@code prd:subcontract:query}</p>
     *
     * @param subcontractId 委外订单ID
     * @return 收货单列表
     */
    @SaCheckPermission("prd:subcontract:query")
    @GetMapping("/list/{subcontractId}")
    public R<List<?>> list(@PathVariable Long subcontractId) {
        return R.ok(receiveService.listBySubcontract(subcontractId));
    }

    /**
     * 查询收货单详情
     * <p>权限标识：{@code prd:subcontract:query}</p>
     *
     * @param receiveId 收货单ID
     * @return 收货单详情
     */
    @SaCheckPermission("prd:subcontract:query")
    @GetMapping("/{receiveId}")
    public R<?> getInfo(@PathVariable Long receiveId) {
        return R.ok(receiveService.selectById(receiveId));
    }

    /**
     * 创建收货单
     * <p>权限标识：{@code prd:subcontract:receive}</p>
     *
     * @param bo 收货单业务对象
     * @return 收货单ID
     */
    @SaCheckPermission("prd:subcontract:receive")
    @Log(title = "委外收货单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@RequestBody WmSubcontractReceiveBo bo) {
        return R.ok(receiveService.createReceive(bo));
    }

    /**
     * 删除收货单(仅草稿)
     * <p>权限标识：{@code prd:subcontract:receive}</p>
     *
     * @param receiveId 收货单ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:subcontract:receive")
    @Log(title = "委外收货单删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{receiveId}")
    public R<Void> remove(@PathVariable Long receiveId) {
        return toAjax(receiveService.deleteReceive(receiveId));
    }

    /**
     * 收货过账(自动建批次入库, actualQty 累加, 达量自动完工)
     * <p>权限标识：{@code prd:subcontract:receive}</p>
     *
     * @param receiveId 收货单ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:subcontract:receive")
    @Log(title = "委外收货单过账", businessType = BusinessType.UPDATE)
    @PutMapping("/post/{receiveId}")
    public R<Void> post(@PathVariable Long receiveId) {
        receiveService.postReceive(receiveId);
        return R.ok();
    }
}
