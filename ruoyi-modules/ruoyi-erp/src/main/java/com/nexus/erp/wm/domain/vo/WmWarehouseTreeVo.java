package com.nexus.erp.wm.domain.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 仓库树形结构视图对象
 * <p>
 * 用于仓库-库区-库位三级树形展示，仅包含必要字段。
 *
 * @author nexus
 */
@Data
public class WmWarehouseTreeVo implements Serializable {

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
     * 库区数量
     */
    private Integer areaCount;

    /**
     * 子级库区列表
     */
    private List<WmStorageAreaTreeVo> children;
}
