package com.nexus.erp.wm.domain.vo.pda;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * PDA 收货单详情 (头 + 行)
 */
@Data
public class PdaReceiveDetailVo {

    private Long receiveId;
    private String receiveCode;
    private String status;
    private String vendorName;
    private Long warehouseId;
    private String warehouseName;
    private String receiveDate;
    private String iqcCode;
    private String remark;

    private List<PdaReceiveLineVo> lines;

    @Data
    public static class PdaReceiveLineVo {
        private Long lineId;
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String materialSpec;
        private String baseUomName;
        private String batchCode;
        /** 已收数量 */
        private BigDecimal qtyReceived;
        /** 应收数量(来源到货通知行; 无通知时为 null) */
        private BigDecimal qtyExpected;
        /** 上架库位编码(已指定时) */
        private String locationCode;
        /** 是否需质检 */
        private Boolean iqcRequired;
    }
}
