package com.nexus.mes.lt.domain.bo;

import lombok.Data;

import java.util.List;

/**
 * 工单 FEFO 批次领料请求 BO
 * <p>两步领料第二步: 操作员按 FEFO 推荐确认领料明细 -> 联动扣减批次数量 + 写消耗记录(+可选 SN↔批次绑定)。
 * 作为未来 P0-1 物料拉动(wm)的调用入口。</p>
 *
 * @author Nexus
 */
@Data
public class LtWoPickBo {

    private String commandId;
    private Long sourceAllocationId;
    private Long processId;
    private String operationRef;

    /** 工单ID */
    private Long woId;

    /** 物料ID */
    private Long materialId;

    /** SN ID(可空; 传入则为该 SN 写 SN↔批次绑定, 用于组件消费追溯) */
    private Long snId;

    /** 领料明细(每条含 lotId + consumedQty; woId/materialId 由本 BO 统一覆盖) */
    private List<LtWoConsumptionBo> picks;
}
