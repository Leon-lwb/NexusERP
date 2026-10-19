package com.nexus.common.trace.port;

import com.nexus.common.trace.*;
import java.util.List;

/**
 * 必须加入库存接收事务；调用前已完成全部外部清单分页核验。
 */
public interface ProductionManifestPostingPort {
    /**
     * 在库存接收事务内整体应用已完整核验的产品清单；实际用料随清单一并校验，失败整体回滚。
     *
     * @param manifest 当前修订的冻结完工清单。
     * @param lines 按稳定行序读取的完整单件明细集合。
     * @return 首次已提交的完整入库或无良品交付回执。
     */
    CompletionReceipt post(CompletionManifest manifest, List<CompletionManifestLine> lines);

    /**
     * 在库存接收事务内整体应用已完整核验的产品清单；实际用料随清单一并校验，失败整体回滚。
     *
     * @param manifest 当前修订的冻结完工清单。
     * @param lines 按稳定行序读取的完整单件明细集合。
     * @param allocations 完整的实际组件用料分配集合。
     * @return 首次已提交的完整入库或无良品交付回执。
     */
    default CompletionReceipt post(
            CompletionManifest manifest,
            List<CompletionManifestLine> lines,
            List<CompletionMaterialAllocation> allocations) {
        if (allocations != null && !allocations.isEmpty()) {
            throw new IllegalStateException("目标入库服务尚未接入冻结组件分配");
        }
        return post(manifest, lines);
    }
}
