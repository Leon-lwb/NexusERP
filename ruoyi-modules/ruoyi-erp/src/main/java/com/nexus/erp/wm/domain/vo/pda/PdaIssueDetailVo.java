package com.nexus.erp.wm.domain.vo.pda;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * PDA 备料发料单详情 (行已按拣货顺序)
 */
@Data
public class PdaIssueDetailVo {

    private Long issueId;
    private String issueCode;
    private String status;
    /** 工单号 */
    private String woNo;
    private String warehouseName;
    private String issueDate;

    private List<PdaIssueLineVo> lines;

    @Data
    public static class PdaIssueLineVo {
        private Long lineId;
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String materialSpec;
        private String baseUomName;
        private String batchCode;
        /** 拣货库位编码 */
        private String locationCode;
        /** 应领数量 */
        private BigDecimal qtyToPick;
        /** 已领数量 */
        private BigDecimal qtyPicked;
        /** 行状态 PENDING/PARTIAL/ISSUED */
        private String lineStatus;
    }
}
