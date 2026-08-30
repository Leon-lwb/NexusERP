package com.nexus.erp.wm.domain.vo.pda;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * PDA 移库单详情 (M1 整单执行)
 */
@Data
public class PdaTransferDetailVo {

    private Long transferId;
    /** 移库单号(实体字段为 transferNo) */
    private String transferNo;
    private String status;
    private String fromWarehouseName;
    private String toWarehouseName;
    private String transferDate;

    private List<PdaTransferLineVo> lines;

    @Data
    public static class PdaTransferLineVo {
        private Long lineId;
        private String materialCode;
        private String materialName;
        private String materialSpec;
        private String baseUomName;
        private String batchCode;
        private String fromLocationCode;
        private String toLocationCode;
        private BigDecimal quantity;
    }
}
