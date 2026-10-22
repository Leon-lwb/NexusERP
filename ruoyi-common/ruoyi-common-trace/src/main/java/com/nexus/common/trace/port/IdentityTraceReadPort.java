package com.nexus.common.trace.port;

import com.nexus.common.trace.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模块所属事实的只读追溯契约；单件、批次和工单范围分别提供证据与独立分页。
 */
public interface IdentityTraceReadPort {
    /**
     * 返回本读取器负责的业务模块，供调用入口选择已授权分节。
     *
     * @return 固定的所属业务模块名称。
     */
    String sourceModule();

    /**
     * 读取稳定实物身份的本模块事实，并保留当前分节分页状态。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param identityId ERP 序列号主档主键，作为跨模块稳定实物身份。
     * @param section 当前追溯事实分节。
     * @param cursor 绑定原租户、主体、分节及修订的分页游标。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @return 本模块的单件追溯分节及分页状态。
     */
    IdentityTracePart identity(
            String tenantId, Long identityId, String section, String cursor, int limit);

    /**
     * 在同一本地可重复读快照中读取已分配与未分配两侧关系，明确容量截断。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param scope 证据范围，区分精确单件、批次与工单可能影响。
     * @param sourceId 由来源类型限定的原业务记录标识。
     * @param capacity 本地快照允许的最大关系数，达到上限须明确标记截断。
     * @return 同一快照中的已分配关系、未分配工单范围及容量截断信息。
     */
    default RecallSubjectSnapshot recallSubject(
            String tenantId, String scope, Long sourceId, int capacity) {
        return TraceRecallSnapshots.read(this, tenantId, scope, sourceId, capacity);
    }

    /**
     * 读取批次的本模块数量及来源事实，不将其冒充为逐件分配。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param batchId 库存批次身份；与制造子批次标识分别保存。
     * @param section 当前追溯事实分节。
     * @param cursor 绑定原租户、主体、分节及修订的分页游标。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @return 本模块的批次数量、来源事实及分页状态。
     */
    default BatchTracePart batch(
            String tenantId, Long batchId, String section, String cursor, int limit) {
        return new BatchTracePart(sourceModule(), batchId, "BATCH", Map.of(), List.of());
    }

    /**
     * 查找一组稳定实物身份已经有证据关联的产品批次。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param identityIds 待核验的跨模块稳定身份集合。
     * @return 这些实物身份有来源证据关联的产品批次集合。
     */
    default Set<Long> batchesForIdentities(String tenantId, List<Long> identityIds) {
        return Set.of();
    }

    /**
     * 分页读取由实际组件身份直接影响的产品身份。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param identityId ERP 序列号主档主键，作为跨模块稳定实物身份。
     * @param afterIdentity 上页已处理的最大稳定身份。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @return 当前稳定页及其后续游标，完整结果需继续读取后续分页。
     */
    default TracePage<Long> affectedByIdentity(
            String tenantId, Long identityId, long afterIdentity, int limit) {
        return new TracePage<>(List.of(), null, false);
    }

    /**
     * 读取实际批次消耗中尚未分配到具体产品的工单范围。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param batchId 库存批次身份；与制造子批次标识分别保存。
     * @param afterConsumption 上页已处理的最大实际消耗记录标识。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @return 当前稳定页及其后续游标，完整结果需继续读取后续分页。
     */
    default TracePage<WorkOrderTraceImpact> unallocatedByBatch(
            String tenantId, Long batchId, long afterConsumption, int limit) {
        return new TracePage<>(List.of(), null, false);
    }

    /**
     * 读取实际组件身份消耗中尚未分配到具体产品的工单范围。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param identityId ERP 序列号主档主键，作为跨模块稳定实物身份。
     * @param afterConsumption 上页已处理的最大实际消耗记录标识。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @return 当前稳定页及其后续游标，完整结果需继续读取后续分页。
     */
    default TracePage<WorkOrderTraceImpact> unallocatedByIdentity(
            String tenantId, Long identityId, long afterConsumption, int limit) {
        return new TracePage<>(List.of(), null, false);
    }

    /**
     * 分页读取工单已经登记的实际产品身份，用于明确工单范围召回。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param mesWoId MES 生产工单主键。
     * @param afterIdentity 上页已处理的最大稳定身份。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @return 当前稳定页及其后续游标，完整结果需继续读取后续分页。
     */
    default TracePage<Long> productIdentitiesByWorkOrder(
            String tenantId, Long mesWoId, long afterIdentity, int limit) {
        return new TracePage<>(List.of(), null, false);
    }

    /**
     * 分页读取工单的实际产品批次，独立维护批次范围游标。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param mesWoId MES 生产工单主键。
     * @param afterBatch 上页已处理的最大产品批次标识。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @return 当前稳定页及其后续游标，完整结果需继续读取后续分页。
     */
    default TracePage<Long> productBatchesByWorkOrder(
            String tenantId, Long mesWoId, long afterBatch, int limit) {
        return new TracePage<>(List.of(), null, false);
    }

    /**
     * 分页读取有实际用料分配证明的受影响产品身份。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param batchId 库存批次身份；与制造子批次标识分别保存。
     * @param afterIdentity 上页已处理的最大稳定身份。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @return 当前稳定页及其后续游标，完整结果需继续读取后续分页。
     */
    TracePage<Long> affectedByBatch(String tenantId, Long batchId, long afterIdentity, int limit);
}
