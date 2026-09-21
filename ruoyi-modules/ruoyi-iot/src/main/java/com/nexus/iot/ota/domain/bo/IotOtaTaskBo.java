package com.nexus.iot.ota.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;

/**
 * IoT OTA 升级任务创建业务对象（单设备下发）。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class IotOtaTaskBo extends BaseEntity {

    /**
     * 目标设备ID
     */
    @NotNull(message = "目标设备ID不能为空")
    private Long deviceId;

    /**
     * 固件ID（必须为已发布 RELEASED 状态）
     */
    @NotNull(message = "固件ID不能为空")
    private Long firmwareId;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
