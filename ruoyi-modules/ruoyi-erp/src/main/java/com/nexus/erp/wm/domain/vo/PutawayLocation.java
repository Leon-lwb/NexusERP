package com.nexus.erp.wm.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 上架解析结果
 * <p>
 * 规则驱动下确定的成品入库目标仓库 + 推荐库位。
 * 由 {@code IPutawayStrategyService.resolvePutaway} 返回，供完工入库链路回写库存与流水。
 *
 * @author nexus
 */
@Data
public class PutawayLocation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 目标仓库ID */
    private Long warehouseId;

    /** 目标仓库编码 */
    private String warehouseCode;

    /** 目标仓库名称 */
    private String warehouseName;

    /** 推荐库位ID */
    private Long locationId;

    /** 推荐库位编码 */
    private String locationCode;

    /** 推荐库位名称 */
    private String locationName;
}
