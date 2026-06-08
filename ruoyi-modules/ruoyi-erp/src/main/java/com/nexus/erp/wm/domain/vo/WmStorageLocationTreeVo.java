package com.nexus.erp.wm.domain.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 库位树形结构视图对象
 * <p>
 * 用于仓库-库区-库位三级树形展示，仅包含必要字段。
 *
 * @author nexus
 */
@Data
public class WmStorageLocationTreeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
     * 所属库区ID
     */
    private Long areaId;

    /**
     * 库位类型
     */
    private String locationType;

    /**
     * 是否启用
     */
    private Long enableFlag;

    /**
     * 库位状态
     */
    private Long status;

    /**
     * 占用率(0-100)
     */
    private BigDecimal occupancyRate;
}
