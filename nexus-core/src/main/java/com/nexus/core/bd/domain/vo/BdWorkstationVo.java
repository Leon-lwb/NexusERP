
package com.nexus.core.bd.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

import java.math.BigDecimal;

/**
 * 工作站对象 bd_workstation vo
 *
 * @author nexus
 * @date 2025-11-19
 */
@Data
public class BdWorkstationVo extends BasePageDtoRequest {

    /**
     * 工作站ID
     */
    private Long workstationId;
    /**
     * 工作站编码
     */
    private String workstationCode;
    /**
     * 工作站名称
     */
    private String workstationName;
    /**
     * 所属车间ID
     */
    private Long workshopId;
    /**
     * 车间编码
     */
    private String workshopCode;
    /**
     * 车间名称
     */
    private String workshopName;
    /**
     * 工作中心描述
     */
    private String workstationDesc;
    /**
     * 设备ID
     */
    private Long equipmentId;
    /**
     * 标准操作工人数
     */
    private Long operatorCount;
    /**
     * 标准产能
     */
    private BigDecimal standardCapacity;
    /**
     * 状态
     */
    private Long status;
    /**
     * 备注
     */
    private String remark;

}