package com.nexus.mes.trace;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.trace.port.MaterialPolicyPort;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

/** Existing MES permissions authorize explicit source-backed allocation and immutable correction. */
@RestController @RequestMapping("/mes/lt/trace") @RequiredArgsConstructor
public class MesMaterialTraceController {
    private final MesMaterialTraceWriter writer;
    private final MesProductLotOutputWriter productOutputs;
    private final MaterialPolicyPort policies;
    private final MesTraceContext context;
    private final com.nexus.mes.trace.MesPreparedWorkOrderPolicies preparedPolicies;
    public record LotAllocation(String commandId,String sourceEventId,Long lotId,BigDecimal quantity) { }
    public record SnAllocation(String commandId,Long consumptionId,Long snId,BigDecimal quantity,Long processId,String operationRef) { }
    public record Reversal(String commandId,Long originalEntryId,BigDecimal quantity,String reason) { }
    public record ProductOutput(String commandId,Long cardId,Long stepRecordId,java.util.List<MesProductLotOutputWriter.OutputLine> lines) { }
    /** Physical lot results may be registered before legacy complete-step/dispatch completion. */
    @PostMapping("/product-output") @SaCheckPermission("mes:pro:flowcard:complete")
    public R<java.util.List<Long>> productOutput(@RequestBody ProductOutput body) {
        return R.ok(productOutputs.record(body.commandId(),body.cardId(),body.stepRecordId(),body.lines(),LoginHelper.getUserId()));
    }
    @PostMapping("/lot-allocation") @SaCheckPermission("mes:lt:consumption:add")
    public R<Long> allocateLot(@RequestBody LotAllocation body) {
        return R.ok(writer.allocateLot(body.commandId(),body.sourceEventId(),body.lotId(),body.quantity(),LoginHelper.getUserId()));
    }
    @PostMapping("/sn-consumption") @SaCheckPermission("mes:lt:consumption:add")
    public R<Long> allocateSn(@RequestBody SnAllocation body) {
        context.requireNoTransaction();
        var policy=preparedPolicies.enabled()?preparedPolicies.componentForConsumption(body.commandId(),body.consumptionId()):
            policies.policy(context.tenant(),writer.consumptionMaterial(body.consumptionId()));
        return R.ok(writer.allocateToSn(body.commandId(),body.consumptionId(),body.snId(),body.quantity(),body.processId(),body.operationRef(),LoginHelper.getUserId(),policy));
    }
    public record ComponentCorrection(String commandId,Long manifestId,int cancelledRevision,Long snId,Long componentMaterialId,long expectedVersion,
        java.util.List<MesMaterialTraceWriter.ReverseLine> reversals,java.util.List<MesMaterialTraceWriter.CorrectedAllocation> allocations,String reason) { }
    @PostMapping("/sn-consumption/correct") @SaCheckPermission(value={"mes:pro:work:edit","mes:lt:consumption:add","mes:lt:consumption:remove"})
    public R<MesMaterialTraceWriter.CorrectionResult> correctComponent(@RequestBody ComponentCorrection body) {
        context.requireNoTransaction();
        var policy=preparedPolicies.enabled()?preparedPolicies.componentForSn(body.commandId(),body.snId(),body.componentMaterialId()):
            policies.policy(context.tenant(),body.componentMaterialId());
        return R.ok(writer.correct(new MesMaterialTraceWriter.Correction(body.commandId(),body.manifestId(),body.cancelledRevision(),body.snId(),body.componentMaterialId(),body.expectedVersion(),body.reversals(),body.allocations(),body.reason(),LoginHelper.getUserId()),policy));
    }
    @PostMapping("/sn-consumption/reverse") @SaCheckPermission("mes:lt:consumption:remove")
    public R<Long> reverse(@RequestBody Reversal body) {
        return R.ok(writer.reverse(body.commandId(),body.originalEntryId(),body.quantity(),body.reason(),LoginHelper.getUserId()));
    }
}
