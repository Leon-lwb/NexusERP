package com.nexus.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.wm.domain.bo.WmSubcontractIssueBo;
import com.nexus.erp.wm.service.IWmSubcontractIssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 委外发料单 Controller (PCBA 委外闭环)
 * <p>
 * 备料建议 -> 发料单创建(批次分配可手调) -> 过账扣库存 -> 全部自供行发齐自动推进委外订单。
 * 权限复用委外订单权限点({@code prd:subcontract:query} / {@code prd:subcontract:issue})。</p>
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/sc-issue")
public class WmSubcontractIssueController extends BaseController {

    private final IWmSubcontractIssueService issueService;

    /**
     * 按委外订单查询发料单列表
     * <p>权限标识：{@code prd:subcontract:query}</p>
     *
     * @param subcontractId 委外订单ID
     * @return 发料单列表
     */
    @SaCheckPermission("prd:subcontract:query")
    @GetMapping("/list/{subcontractId}")
    public R<List<?>> list(@PathVariable Long subcontractId) {
        return R.ok(issueService.listBySubcontract(subcontractId));
    }

    /**
     * 查询发料单详情(含行)
     * <p>权限标识：{@code prd:subcontract:query}</p>
     *
     * @param issueId 发料单ID
     * @return {issue, lines}
     */
    @SaCheckPermission("prd:subcontract:query")
    @GetMapping("/{issueId}")
    public R<Map<String, Object>> getInfo(@PathVariable Long issueId) {
        return R.ok(issueService.selectById(issueId));
    }

    /**
     * 备料建议: 自备料行 × 可用库存自动生成 FEFO/FIFO 批次分配
     * <p>权限标识：{@code prd:subcontract:query}</p>
     *
     * @param subcontractId 委外订单ID
     * @return 分配建议列表(可编辑后随发料单提交)
     */
    @SaCheckPermission("prd:subcontract:query")
    @GetMapping("/suggest/{subcontractId}")
    public R<List<Map<String, Object>>> suggest(@PathVariable Long subcontractId) {
        return R.ok(issueService.suggestAllocations(subcontractId));
    }

    /**
     * 创建发料单(含行, allocJson 可空=过账时自动分配)
     * <p>权限标识：{@code prd:subcontract:issue}</p>
     *
     * @param bo 发料单业务对象
     * @return 发料单ID
     */
    @SaCheckPermission("prd:subcontract:issue")
    @Log(title = "委外发料单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@RequestBody WmSubcontractIssueBo bo) {
        return R.ok(issueService.createIssue(bo));
    }

    /**
     * 删除发料单(仅草稿)
     * <p>权限标识：{@code prd:subcontract:issue}</p>
     *
     * @param issueId 发料单ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:subcontract:issue")
    @Log(title = "委外发料单删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{issueId}")
    public R<Void> remove(@PathVariable Long issueId) {
        return toAjax(issueService.deleteIssue(issueId));
    }

    /**
     * 发料过账(订单维度加锁, 扣减库存写流水, 全部发齐自动推进订单)
     * <p>权限标识：{@code prd:subcontract:issue}</p>
     *
     * @param issueId 发料单ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:subcontract:issue")
    @Log(title = "委外发料单过账", businessType = BusinessType.UPDATE)
    @PutMapping("/post/{issueId}")
    public R<Void> post(@PathVariable Long issueId) {
        issueService.postIssue(issueId);
        return R.ok();
    }
}
