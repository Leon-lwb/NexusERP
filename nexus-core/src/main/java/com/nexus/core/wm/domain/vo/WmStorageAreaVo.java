
package com.nexus.core.wm.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

import java.math.BigDecimal;

/**
 * 库区对象 wm_storage_area vo
 *
 * @author nexus
 * @date 2025-12-01
 */
@Data
public class WmStorageAreaVo extends BasePageDtoRequest {

    /**
     * 库区ID
     */
    private Long areaId;
    /**
     * 库区编码
     */
    private String areaCode;
    /**
     * 库区名称
     */
    private String areaName;
    /**
     * 仓库ID
     */
    private Long warehouseId;
    /**
     * 面积
     */
    private BigDecimal area;
    /**
     * 是否开启库位管理
     */
    private Long locationFlag;
    /**
     * 是否启用
     */
    private Long enableFlag;
    /**
     * 是否冻结
     */
    private Long frozenFlag;
    /**
     * 布局类型: GRID-网格, RACK-货架, FLUID-流体, BULK-散货
     */
    private String layoutType;

}