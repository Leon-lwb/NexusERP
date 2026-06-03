package com.nexus.erp.wm.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;

import java.util.Date;

/**
 * 条码实例业务对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WmBarcodeBo extends BaseEntity {

    private Long barcodeId;

    @NotBlank(message = "条码不能为空")
    private String barcodeCode;

    private Long configId;

    private String configCode;

    private String bizType;

    private Long bizId;

    private Long bizLineId;

    private Long materialId;

    private Long batchId;

    private String batchCode;

    private Long snId;

    private Long stockId;

    private String status;

    private Date generateTime;

    private Date bindTime;

    private Date expireTime;

    private String remark;
}
