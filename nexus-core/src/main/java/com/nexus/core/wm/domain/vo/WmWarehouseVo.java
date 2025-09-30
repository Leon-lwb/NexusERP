
package com.nexus.core.wm.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

import java.math.BigDecimal;

/**
 * 仓库设置对象 wm_warehouse vo
 *
 * @author nexus
 * @date 2025-09-30
 */
@Data
public class WmWarehouseVo extends BasePageDtoRequest {

    /**
     * 仓库ID
     */
    private Long warehouseId;
    /**
     * 仓库编码
     */
    private String warehouseCode;
    /**
     * 仓库名称
     */
    private String warehouseName;
    /**
     * 位置
     */
    private String location;
    /**
     * 面积
     */
    private BigDecimal area;
    /**
     * 负责人
     */
    private String manager;
    /**
     * 仓库状态
     */
    private Long status;

}