
package com.nexus.core.wm.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库位对象 wm_storage_location vo
 *
 * @author nexus
 * @date 2025-12-02
 */
@Data
public class WmStorageLocationVo extends BasePageDtoRequest {

    /**
     * 库位ID
     */
    private Long locationId;
    /**
     * 库位编码
     */
    private String locationCode;
    /**
     * 库位名称
     */
    private String locationName;
    /**
     * 面积
     */
    private BigDecimal area;
    /**
     * 最大载重量
     */
    private BigDecimal maxLoa;
    /**
     * 是否允许产品混放
     */
    private Long productMixing;
    /**
     * 是否允许批次混放
     */
    private Long batchMixing;
    /**
     * 是否启用
     */
    private Long enableFlag;
    /**
     * 是否冻结
     */
    private Long frozenFlag;
    /**
     * 库位类型
     */
    private String locationType;
    /**
     * 库位状态
     */
    private Long status;
    /**
     * 占用率(0-100)
     */
    private BigDecimal occupancyRate;
    /**
     * 最后入库时间
     */
    private LocalDateTime lastInboundTime;
    /**
     * 最后出库时间
     */
    private LocalDateTime lastOutboundTime;

}