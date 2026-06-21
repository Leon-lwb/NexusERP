package com.nexus.mes.pro.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 组件追溯结果VO
 * <p>
 * backward(反向): 成品SN/工单 -> 所需组件清单(快照) + 实际装配的组件SN
 * forward(正向): 组件物料/SN -> 被哪些成品工单消耗 + 父SN链
 */
@Data
public class TraceabilityResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // === 工单信息 ===
    private Long mesWoId;
    private String woNo;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String woStatus;

    // === 反向追溯: 成品 -> 组件 ===
    /** MBOM快照要求的组件清单 */
    private List<ProWorkOrderBomLineVo> requiredComponents;
    /** 实际装配的组件SN列表(通过pro_sn.parent_sn_id关联) */
    private List<SnInfo> assembledComponentSns;

    // === 正向追溯: 组件 -> 成品 ===
    /** 消费该组件的工单列表 */
    private List<TraceabilityResult> consumingWorkOrders;
    /** 父SN链(组件SN被装配到哪个成品SN) */
    private List<SnInfo> parentSnChain;

    @Data
    public static class SnInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long snId;
        private String snCode;
        private Long mesWoId;
        private String woNo;
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String snStatus;
        private Long parentSnId;
        private String parentSnCode;
    }
}
