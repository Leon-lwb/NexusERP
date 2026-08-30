package com.nexus.erp.wm.domain.vo.pda;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * PDA 上料交接单详情
 */
@Data
public class PdaHandoverDetailVo {

    private Long handoverId;
    private String handoverNo;
    /** 来源工单号 */
    private String woNo;
    private String status;
    /** 源仓库 */
    private String warehouseName;
    private String handoverTime;
    /** 签收人(已签收时) */
    private String signName;
    private String signTime;

    private List<PdaHandoverLineVo> lines;

    @Data
    public static class PdaHandoverLineVo {
        private Long lineId;
        private String materialCode;
        private String materialName;
        private String materialSpec;
        private String baseUomName;
        private String batchCode;
        /** 源库位编码 */
        private String fromLocationCode;
        /** 线边库位编码 */
        private String toLocationCode;
        /** 计划交接数量 */
        private BigDecimal planQty;
        /** 实交数量(核销累计, 未核销为 0) */
        private BigDecimal actualQty;
    }
}
