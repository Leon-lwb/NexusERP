package org.dromara.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

/**
 * 库存调拨行对象 wm_transfer_line
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_transfer_line")
public class WmTransferLine extends TenantEntity {

    @TableId(value = "line_id")
    private Long lineId;

    private Long transferId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String materialSpec;
    private String baseUomId;
    private String baseUomName;
    private Long batchId;
    private String batchCode;
    private Long fromLocationId;
    private String fromLocationCode;
    private String fromLocationName;
    private Long toLocationId;
    private String toLocationCode;
    private String toLocationName;
    private BigDecimal quantity;
    private String remark;
}
