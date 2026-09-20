package com.nexus.iot.ota.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;

/**
 * IoT OTA 升级任务创建业务对象（按分组批量下发）。
 * <p>
 * 复用既有设备分组关系表：{@code IIotDeviceGroupService.selectDeviceIds(groupId, includeSubGroup)}
 * 解析出目标设备集合后逐台建任务；单台设备被禁用/已存在进行中任务时**跳过并逐条回传原因**，
 * 不影响其余设备。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class IotOtaBatchTaskBo extends BaseEntity {

    /**
     * 固件ID（必须为已发布 RELEASED 状态）
     */
    @NotNull(message = "固件ID不能为空")
    private Long firmwareId;

    /**
     * 目标分组ID
     */
    @NotNull(message = "目标分组ID不能为空")
    private Long groupId;

    /**
     * 是否包含子分组（默认仅本分组）
     */
    private Boolean includeSubGroup;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
