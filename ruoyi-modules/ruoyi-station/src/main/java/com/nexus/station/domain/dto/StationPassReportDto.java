package com.nexus.station.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * SN 过站单件报工请求 (M2 契约 C3)
 *
 * @author Nexus
 */
@Data
public class StationPassReportDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SN编码 */
    private String snCode;

    /** 工位编码 */
    private String stationCode;

    /** 操作员用户ID(C1 上岗返回, 可空) */
    private Long operatorId;

    /** 操作员工号(可空) */
    private String operatorCode;

    /** 结果: PASS/FAIL */
    private String result;

    /** 不良代码(FAIL 时传) */
    private List<String> defectCodes;

    /** 客户端时间戳(毫秒, 幂等键组成部分) */
    private Long clientTs;
}
