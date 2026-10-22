package com.nexus.mes.trace;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.trace.port.MaterialPolicyPort;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 沿用制造业务权限，仅开放有来源凭据的用料分配与不可变更正入口。
 */
@RestController
@RequestMapping("/mes/lt/trace")
@RequiredArgsConstructor
public class MesMaterialTraceController {
    private final MesMaterialTraceWriter writer;
    private final MesProductLotOutputWriter productOutputs;
    private final MaterialPolicyPort policies;
    private final MesTraceContext context;
    private final MesPreparedWorkOrderPolicies preparedPolicies;

    /**
     * 将来源额度分配到实际制造子批次的请求。
     *
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param sourceEventId 已提交的原来源事件标识。
     * @param lotId 制造模块本地子批次标识。
     * @param quantity 本次分配给制造子批次的来源额度。
     */
    public record LotAllocation(
            String commandId, String sourceEventId, Long lotId, BigDecimal quantity) {}

    /**
     * 将已发生消耗分配到具体产品身份的请求。
     *
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param consumptionId 已经发生的工单实际消耗记录。
     * @param snId 制造模块本地单件主键；跨模块关联另取稳定身份。
     * @param quantity 从原实际消耗分配给该产品单件的组件数量。
     * @param processId 实际发生用料或过站的工序标识。
     * @param operationRef 原作业记录的稳定引用。
     */
    public record SnAllocation(
            String commandId,
            Long consumptionId,
            Long snId,
            BigDecimal quantity,
            Long processId,
            String operationRef) {}

    /**
     * 关联原实际用料分录的追加冲正请求。
     *
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param originalEntryId 本次冲正引用的原用料分录。
     * @param quantity 本次从原用料分录冲正的数量。
     * @param reason 与原任务或原事实关联的明确操作原因。
     */
    public record Reversal(
            String commandId, Long originalEntryId, BigDecimal quantity, String reason) {}

    /**
     * 实际产品批次的合格与报废产出。
     *
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param cardId 当前工单下的合法流转卡标识。
     * @param stepRecordId 原工序执行或过站记录。
     * @param lines 本次批次产品产出明细，保留各来源分配与实际数量。
     */
    public record ProductOutput(
            String commandId,
            Long cardId,
            Long stepRecordId,
            List<MesProductLotOutputWriter.OutputLine> lines) {}

    /** 允许在既有末道报工或派工完成前登记实际产品批次产出。 */
    @PostMapping("/product-output")
    @SaCheckPermission("mes:pro:flowcard:complete")
    public R<List<Long>> productOutput(@RequestBody ProductOutput body) {
        return R.ok(
                productOutputs.record(
                        body.commandId(),
                        body.cardId(),
                        body.stepRecordId(),
                        body.lines(),
                        LoginHelper.getUserId()));
    }

    @PostMapping("/lot-allocation")
    @SaCheckPermission("mes:lt:consumption:add")
    public R<Long> allocateLot(@RequestBody LotAllocation body) {
        return R.ok(
                writer.allocateLot(
                        body.commandId(),
                        body.sourceEventId(),
                        body.lotId(),
                        body.quantity(),
                        LoginHelper.getUserId()));
    }

    @PostMapping("/sn-consumption")
    @SaCheckPermission("mes:lt:consumption:add")
    public R<Long> allocateSn(@RequestBody SnAllocation body) {
        context.requireNoTransaction();
        var policy =
                preparedPolicies.enabled()
                        ? preparedPolicies.componentForConsumption(
                                body.commandId(), body.consumptionId())
                        : policies.policy(
                                context.tenant(), writer.consumptionMaterial(body.consumptionId()));
        return R.ok(
                writer.allocateToSn(
                        body.commandId(),
                        body.consumptionId(),
                        body.snId(),
                        body.quantity(),
                        body.processId(),
                        body.operationRef(),
                        LoginHelper.getUserId(),
                        policy));
    }

    /**
     * 清单取消握手完成后的组件追加更正请求。
     *
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param manifestId 不可变完工清单标识。
     * @param cancelledRevision 已经通过取消握手释放的旧清单修订。
     * @param snId 制造模块本地单件主键；跨模块关联另取稳定身份。
     * @param componentMaterialId 实际消耗的组件物料标识。
     * @param expectedVersion 调用方预期的原记录版本，用于并发条件校验。
     * @param reversals 按原分录追加的冲正集合。
     * @param allocations 完整的实际组件用料分配集合。
     * @param reason 与原任务或原事实关联的明确操作原因。
     */
    public record ComponentCorrection(
            String commandId,
            Long manifestId,
            int cancelledRevision,
            Long snId,
            Long componentMaterialId,
            long expectedVersion,
            List<MesMaterialTraceWriter.ReverseLine> reversals,
            List<MesMaterialTraceWriter.CorrectedAllocation> allocations,
            String reason) {}

    @PostMapping("/sn-consumption/correct")
    @SaCheckPermission(
            value = {"mes:pro:work:edit", "mes:lt:consumption:add", "mes:lt:consumption:remove"})
    public R<MesMaterialTraceWriter.CorrectionResult> correctComponent(
            @RequestBody ComponentCorrection body) {
        context.requireNoTransaction();
        var policy =
                preparedPolicies.enabled()
                        ? preparedPolicies.componentForSn(
                                body.commandId(), body.snId(), body.componentMaterialId())
                        : policies.policy(context.tenant(), body.componentMaterialId());
        return R.ok(
                writer.correct(
                        new MesMaterialTraceWriter.Correction(
                                body.commandId(),
                                body.manifestId(),
                                body.cancelledRevision(),
                                body.snId(),
                                body.componentMaterialId(),
                                body.expectedVersion(),
                                body.reversals(),
                                body.allocations(),
                                body.reason(),
                                LoginHelper.getUserId()),
                        policy));
    }

    @PostMapping("/sn-consumption/reverse")
    @SaCheckPermission("mes:lt:consumption:remove")
    public R<Long> reverse(@RequestBody Reversal body) {
        return R.ok(
                writer.reverse(
                        body.commandId(),
                        body.originalEntryId(),
                        body.quantity(),
                        body.reason(),
                        LoginHelper.getUserId()));
    }
}
