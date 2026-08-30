package com.nexus.erp.wm.domain.vo.pda;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * PDA 退料单详情 (三类: 良品/不良品/报废)
 */
@Data
public class PdaReturnDetailVo {

    private Long returnId;
    private String returnNo;
    /** GOOD/DEFECT/SCRAP */
    private String returnType;
    private String status;
    private String warehouseName;
    private String sourceDoc;
    private String returnDate;

    private List<PdaReturnLineVo> lines;

    @Data
    public static class PdaReturnLineVo {
        private Long lineId;
        private String materialCode;
        private String materialName;
        private String materialSpec;
        private String baseUomName;
        private String batchCode;
        private String locationCode;
        /** 计划退料数量 */
        private BigDecimal planQty;
        /** 实退数量(核销累计, 未核销为 0) */
        private BigDecimal actualQty;
    }
}
