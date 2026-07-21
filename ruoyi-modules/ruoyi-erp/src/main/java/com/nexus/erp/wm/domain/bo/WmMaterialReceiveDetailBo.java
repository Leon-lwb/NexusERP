package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmMaterialReceiveDetail;

import java.math.BigDecimal;

/**
 * 物料入库单明细分业务对象 wm_material_receive_detail
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmMaterialReceiveDetail.class, reverseConvertGenerate = false)
public class WmMaterialReceiveDetailBo extends BaseEntity {

    /**
     * 明细ID
     */
    private Long detailId;

    /**
     * 入库单行ID
     */
    private Long lineId;

    /**
     * 入库单ID
     */
    private Long receiveId;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料编码
     */
    private String materialCode;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 物料规格
     */
    private String materialSpec;

    /**
     * 基本单位ID
     */
    private String baseUomId;

    /**
     * 基本单位名称
     */
    private String baseUomName;

    /**
     * 数量
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
     * 序列号ID
     */
    private Long snId;

    /**
     * 序列号编码
     */
    private String snCode;

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

    /**
     * 备注
     */
    private String remark;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;

    /**
     * 批量操作ID集合
     */
    private Long[] ids;
}
