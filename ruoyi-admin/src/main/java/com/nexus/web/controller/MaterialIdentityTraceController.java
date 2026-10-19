package com.nexus.web.controller;

import com.nexus.common.core.domain.R;
import com.nexus.web.service.MaterialIdentityTraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * 统一追溯查询入口；在认证权限范围内分别查询单件、批次及可能影响范围。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping({"/erp/wm/trace", "/app/pda/trace"})
public class MaterialIdentityTraceController {
    private final MaterialIdentityTraceService trace;

    /**
     * 查询当前权限范围内的单件追溯分节。
     *
     * @param identityId ERP 序列号主档主键，作为跨模块稳定实物身份。
     * @param module 事实所属业务模块。
     * @param section 当前追溯事实分节。
     * @param cursor 绑定原租户、主体、分节及修订的分页游标。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @return 包含本页单件追溯事实、权限状态和后续游标的响应。
     */
    @GetMapping("/identity/{identityId}")
    public R<Map<String, Object>> identity(
            @PathVariable Long identityId,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "500") int limit) {
        return R.ok(trace.identity(identityId, module, section, cursor, limit));
    }

    /**
     * 查询当前权限范围内的批次数量及来源分节。
     *
     * @param batchId 库存批次身份；与制造子批次标识分别保存。
     * @param module 事实所属业务模块。
     * @param section 当前追溯事实分节。
     * @param cursor 绑定原租户、主体、分节及修订的分页游标。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @return 包含本页批次追溯事实、权限状态和后续游标的响应。
     */
    @GetMapping("/batch/{batchId}")
    public R<Map<String, Object>> batch(
            @PathVariable Long batchId,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "500") int limit) {
        return R.ok(trace.batch(batchId, module, section, cursor, limit));
    }

    /**
     * 分页查询批次可能影响范围，截断和未分配工单单独返回。
     *
     * @param batchId 库存批次身份；与制造子批次标识分别保存。
     * @param afterIdentity 上页已处理的最大稳定身份。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @param scopeCursor 工单范围的独立分页位置。
     * @param batchCursor 受影响批次的独立分页位置。
     * @return 包含受影响产品、工单及批次范围和截断标记的响应。
     */
    @GetMapping("/recall")
    public R<Map<String, Object>> recall(
            @RequestParam Long batchId,
            @RequestParam(defaultValue = "0") long afterIdentity,
            @RequestParam(defaultValue = "500") int limit,
            @RequestParam(defaultValue = "10") int depth,
            @RequestParam(required = false) String scopeCursor,
            @RequestParam(required = false) String batchCursor) {
        return R.ok(trace.recall(batchId, afterIdentity, limit, depth, scopeCursor, batchCursor));
    }

    /**
     * 分页查询组件单件的产品及客户去向。
     *
     * @param identityId ERP 序列号主档主键，作为跨模块稳定实物身份。
     * @param afterIdentity 上页已处理的最大稳定身份。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @param scopeCursor 工单范围的独立分页位置。
     * @param batchCursor 受影响批次的独立分页位置。
     * @return 包含组件实际去向、工单及批次范围和截断标记的响应。
     */
    @GetMapping("/where-used/{identityId}")
    public R<Map<String, Object>> whereUsed(
            @PathVariable Long identityId,
            @RequestParam(defaultValue = "0") long afterIdentity,
            @RequestParam(defaultValue = "500") int limit,
            @RequestParam(defaultValue = "10") int depth,
            @RequestParam(required = false) String scopeCursor,
            @RequestParam(required = false) String batchCursor) {
        return R.ok(
                trace.whereUsed(identityId, afterIdentity, limit, depth, scopeCursor, batchCursor));
    }
}
