package com.nexus.mes.pro.service;

import com.nexus.mes.pro.domain.vo.TraceabilityResult;

import java.util.List;

/**
 * 组件追溯服务
 * <p>
 * 基于 pro_work_order_bom_line 快照(所需组件) + pro_sn.parent_sn_id(实际装配链)
 * 实现组件级正反向追溯。
 * <p>
 * 追溯粒度: 物料级 + SN装配级(不含批次/批号级,需未来组件消费记录表)
 */
public interface IProTraceabilityService {

    /**
     * 反向追溯: 成品SN/工单 -> 所需组件清单 + 实际装配的组件SN
     *
     * @param mesWoId 工单ID
     * @param snCode  成品SN编码(可空,为空时仅返回工单级追溯)
     * @return 追溯结果(含MBOM快照要求的组件 + 实际装配的组件SN)
     */
    TraceabilityResult backward(Long mesWoId, String snCode);

    /**
     * 正向追溯: 组件物料/SN -> 被哪些成品工单消耗
     *
     * @param componentMaterialId 组件物料ID(与componentSnCode二选一)
     * @param componentSnCode     组件SN编码(可空)
     * @return 追溯结果列表(每个消费该组件的工单一条)
     */
    List<TraceabilityResult> forward(Long componentMaterialId, String componentSnCode);
}
