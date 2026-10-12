package com.nexus.web.domain.vo;

import com.nexus.erp.wm.domain.vo.WmTraceResultVo;
import com.nexus.mes.pro.domain.vo.ProSnVo;
import lombok.Data;

import java.util.Map;

/**
 * PDA 统一追溯查询结果 (M3 批次4 门面)
 * <p>
 * 一次输入(批次/SN/物料编码/批次号)聚合既有追溯能力:
 * ERP 库存流水正反向追溯 + MES 批次链 + 制造 SN 履历。各段按原有查询权限返回。
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

    /** MES: 保留批次链原有键；manufacturing 为独立 PdaManufacturingTraceVo，可与批次链同时存在。 */
    private Map<String, Object> mes;

    @Data
    public static class ResolvedVo {
        private SnVo sn;
        /** Independent MES match; warehouse SN precedence and meaning are unchanged. */
        private ProSnVo manufacturingSn;
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
