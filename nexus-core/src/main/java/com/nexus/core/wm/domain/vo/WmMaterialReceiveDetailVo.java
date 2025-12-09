
package com.nexus.core.wm.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

import java.math.BigDecimal;

/**
 * 物料入库单明细对象 wm_material_receive_detail vo
 *
 * @author nexus
 * @date 2025-12-09
 */
@Data
public class WmMaterialReceiveDetailVo extends BasePageDtoRequest {

    /**
     * 明细ID
     */
    private Long detailId;
    /**
     * 行ID
     */
    private Long lineId;
    /**
     * 入库单ID
     */
    private Long receiveId;
    /**
     * 产品物料ID
     */
    private Long materialId;
    /**
     * 产品物料编码
     */
    private String materialCode;
    /**
     * 产品物料名称
     */
    private String materialName;
    /**
     * 规格型号
     */
    private String materialSpec;
    /**
     * 单位
     */
    private String baseUomId;
    /**
     * 单位名称
     */
    private String baseUomName;
    /**
     * 入库数量
     */
    private BigDecimal quantity;
    /**
     * 批次ID
     */
    private Long batchId;
    /**
     * 批次号
     */
    private String batchCode;
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

}