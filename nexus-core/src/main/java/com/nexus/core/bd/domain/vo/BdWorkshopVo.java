
package com.nexus.core.bd.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

import java.math.BigDecimal;

/**
 * 车间设置对象 bd_workshop vo
 *
 * @author nexus
 * @date 2025-11-18
 */
@Data
public class BdWorkshopVo extends BasePageDtoRequest {

    /**
     * 车间ID
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
     * 车间描述
     */
    private String workshopDesc;
    /**
     * 车间主任
     */
    private String managerName;
    /**
     * 产能（标准工时/天）
     */
    private BigDecimal capacity;
    /**
     * 状态
     */
    private Long status;
    /**
     * 排序
     */
    private Long sortOrder;

}