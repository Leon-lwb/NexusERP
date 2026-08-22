package com.nexus.station.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工位上料校验+登记请求 (M3 契约 C19)
 *
 * @author Nexus
 */
@Data
public class StationMaterialLoadDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工位编码 */
    private String stationCode;

    /** 扫描的批次码(B前缀)/条码实例(RC前缀) */
    private String barcode;

    /** 料站号(可空, 贴片机 feeder 位号等) */
    private String feederSlot;

    /** 上料人工号(留痕) */
    private String operatorCode;

    /** 上料人姓名(留痕) */
    private String operatorName;
}
