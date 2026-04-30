package org.dromara.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物料入库单行对象 wm_material_receive_line
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_material_receive_line")
public class WmMaterialReceiveLine extends TenantEntity {

    @TableId(value = "line_id")
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

    @TableField(exist = false)
    private List<WmMaterialReceiveDetail> wmMaterialReceiveLineDetailList;

    @TableLogic
    private String delFlag;
}
