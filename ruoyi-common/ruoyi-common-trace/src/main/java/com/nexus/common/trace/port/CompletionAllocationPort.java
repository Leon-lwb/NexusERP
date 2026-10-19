package com.nexus.common.trace.port;

import com.nexus.common.trace.*;

/**
 * 不可变组件消耗清单的读取契约，与产品清单采用相同租户、清单标识和修订。
 */
public interface CompletionAllocationPort {
    /**
     * 读取与指定完工修订绑定的组件清单头，供整体页数、数量和摘要核验。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param manifestId 不可变完工清单标识。
     * @param revision 冻结清单修订号，必须与游标及事件一致。
     * @return 指定修订的实际用料汇总及完整摘要。
     */
    CompletionAllocationManifest allocationManifest(String tenantId, Long manifestId, int revision);

    /**
     * 按稳定游标读取该修订的实际用料分页，单页返回不能替代完整组件清单。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param manifestId 不可变完工清单标识。
     * @param revision 冻结清单修订号，必须与游标及事件一致。
     * @param cursor 绑定原租户、主体、分节及修订的分页游标。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @return 当前稳定页及其后续游标，完整结果需继续读取后续分页。
     */
    TracePage<CompletionMaterialAllocation> allocations(
            String tenantId, Long manifestId, int revision, String cursor, int limit);
}
