package com.nexus.erp.wm.domain.vo.pda;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * PDA 盘点单详情 (M1 明盘: 展示账面数)
 */
@Data
public class PdaStocktakingDetailVo {

    private Long planId;
    private String planCode;
    private String planName;
    private String status;
    private String warehouseName;

    private List<PdaStocktakingLineVo> lines;

    @Data
    public static class PdaStocktakingLineVo {
        private Long lineId;
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String materialSpec;
        private String baseUomName;
        private String batchCode;
        private String locationCode;
        /** 账面数量 */
        private BigDecimal bookQty;
        /** 实盘数量(未录入时后端返回 0, 行状态另见 lineStatus) */
        private BigDecimal countedQty;
        /** 行状态 PENDING/POSTED/BALANCED */
        private String lineStatus;
    }
}
