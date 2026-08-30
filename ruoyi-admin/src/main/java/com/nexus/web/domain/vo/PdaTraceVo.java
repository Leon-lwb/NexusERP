package com.nexus.web.domain.vo;

import com.nexus.erp.wm.domain.vo.WmTraceResultVo;
import lombok.Data;

import java.util.Map;

/**
 * PDA 统一追溯查询结果 (M3 批次4 门面)
 * <p>
 * 一次输入(批次/SN/物料编码/批次号)聚合三套既有追溯能力:
 * ERP 库存流水正反向追溯(wm_material_stock_tx 递归) + MES 批次链(lt_lot 消耗/绑定/工单/SN)。
 * 未命中维度为 null, 前端按非空段渲染。
 */
@Data
public class PdaTraceVo {

    private String keyword;

    /** 关键字解析结果 */
    private ResolvedVo resolved;

    /** ERP 去向追溯(该料用到了哪些产品) */
    private WmTraceResultVo forward;

    /** ERP 来源追溯(该料的来源单据链) */
    private WmTraceResultVo backward;

    /** MES 批次链 { lot, consumptions, bindings, woIds, snIds } */
    private Map<String, Object> mes;

    @Data
    public static class ResolvedVo {
        private SnVo sn;
        private BatchVo batch;
        private MaterialVo material;
        private LotVo lot;
    }

    @Data
    public static class SnVo {
        private Long snId;
        private String snCode;
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String batchCode;
    }

    @Data
    public static class BatchVo {
        private Long batchId;
        private String batchCode;
        private Long materialId;
        private String materialCode;
        private String materialName;
    }

    @Data
    public static class MaterialVo {
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String materialSpec;
    }

    @Data
    public static class LotVo {
        private Long lotId;
        private String lotCode;
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String status;
        private String qcStatus;
    }
}
