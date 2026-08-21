package com.nexus.station.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * SN 过站四重校验请求 (M2 契约 C2)
 *
 * @author Nexus
 */
@Data
public class StationPassValidateDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SN编码 */
    private String snCode;

    /** 工位编码 */
    private String stationCode;
}
