package com.nexus.erp.wm.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 仓库 3D 可视化聚合视图对象
 * <p>
 * 用于 3D 仓库预览，包含仓库基本信息、库区列表（含库位）及库存状态汇总。
 * 非 {@code @AutoMapper} 映射，由 Service 内存组装。
 *
 * @author nexus
 * @date 2026-06-30
 */
@Data
public class WmWarehouse3dVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
     * 仓库状态
     */
    private Long status;

    /**
     * 库区列表
     */
    private List<WmStorageArea3dVo> areas;

    /**
     * 库位总数
     */
    private Integer totalLocations;

    /**
     * 占用库位数
     */
    private Integer occupiedLocations;

    /**
     * 空置库位数
     */
    private Integer emptyLocations;

    /**
     * 锁定库位数
     */
    private Integer lockedLocations;

    /**
     * 预警库位数
     */
    private Integer warningLocations;
}
