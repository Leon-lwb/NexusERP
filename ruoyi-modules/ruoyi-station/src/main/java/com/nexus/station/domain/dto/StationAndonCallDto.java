package com.nexus.station.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 安灯呼叫请求 (M2 契约 C7)
 *
 * @author Nexus
 */
@Data
public class StationAndonCallDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 呼叫类型: MATERIAL/REPAIR/PERSON/QUALITY */
    private String callType;

    /** 呼叫原因 */
    private String callReason;

    /** 工位编码 */
    private String stationCode;

    /** 呼叫人工号 */
    private String operatorCode;

    /** 工单编号(可空) */
    private String woNo;

    /** 流转卡编号(可空) */
    private String cardNo;
}
