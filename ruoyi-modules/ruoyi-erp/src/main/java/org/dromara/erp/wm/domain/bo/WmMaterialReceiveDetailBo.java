package org.dromara.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.erp.wm.domain.WmMaterialReceiveDetail;

import java.math.BigDecimal;

/**
 * 物料入库单明细分业务对象 wm_material_receive_detail
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmMaterialReceiveDetail.class, reverseConvertGenerate = false)
public class WmMaterialReceiveDetailBo extends BaseEntity {

    private Long detailId;

    private Long lineId;
    private Long receiveId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String materialSpec;
    private String baseUomId;
    private String baseUomName;
    private BigDecimal quantity;
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

    private Long[] ids;
}
