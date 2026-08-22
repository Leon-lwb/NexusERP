package com.nexus.mes.pro.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新工单工序固件请求(生产负责人在工单下达前确认/更改固件版本)
 * <p>仅 CREATED 状态工单可操作; 仅 FLASH 工序可绑定; 固件须 RELEASED 且物料匹配工单产品。</p>
 *
 * @author Nexus
 */
@Data
public class UpdateStepFirmwareBo {

    /** 工序步骤ID */
    @NotNull(message = "工序步骤ID不能为空")
    private Long stepId;

    /** 固件ID(mes_firmware, 仅 RELEASED) */
    @NotNull(message = "固件ID不能为空")
    private Long firmwareId;
}
