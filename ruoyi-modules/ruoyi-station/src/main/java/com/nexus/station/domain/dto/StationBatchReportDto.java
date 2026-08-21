package com.nexus.station.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 批量计数报工请求 (M2 契约 C4)
 *
 * @author Nexus
 */
@Data
public class StationBatchReportDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工位编码 */
    private String stationCode;

    /** 操作员工号(可空) */
    private String operatorCode;

    /** 良品数量 */
    private BigDecimal goodQty;

    /** 不良明细 */
    private List<DefectItem> defects;

    /** 客户端时间戳(毫秒, 幂等键组成部分) */
    private Long clientTs;

    /**
     * 不良明细项
     */
    @Data
    public static class DefectItem implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 不良代码 */
        private String code;

        /** 数量 */
        private BigDecimal qty;
    }
}
