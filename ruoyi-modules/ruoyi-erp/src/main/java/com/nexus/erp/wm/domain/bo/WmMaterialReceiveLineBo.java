package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmMaterialReceiveLine;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物料入库单行业务对象 wm_material_receive_line
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmMaterialReceiveLine.class, reverseConvertGenerate = false)
public class WmMaterialReceiveLineBo extends BaseEntity {

    private Long lineId;

    private Long receiveId;
    private Long sourceDocLineId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String materialSpec;
    private String baseUomId;
    private String baseUomName;
    private BigDecimal quantityReceived;
    private Long batchId;
    private String batchCode;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private Long areaId;
    private String areaCode;
    private String areaName;
    private Long locationId;
    private String locationCode;
    private String locationName;
    private String remark;
    private String attr1;
    private String attr2;
    private Long attr3;
    private Long attr4;

    private List<WmMaterialReceiveDetailBo> wmMaterialReceiveLineDetailList;

    private Long[] ids;
}
