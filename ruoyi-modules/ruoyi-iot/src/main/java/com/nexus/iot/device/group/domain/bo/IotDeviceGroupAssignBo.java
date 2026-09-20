package com.nexus.iot.device.group.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 设备批量挂分组 / 批量打标签请求体。
 * <p>
 * 挂分组：{@code groupId} 必填；{@code tagIds} 用于批量打标签。
 * 两个动作共用同一 DTO 以减少前端请求体类型数量，服务端按字段存在性分派。
 *
 * @author nexus
 */
@Data
public class IotDeviceGroupAssignBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 待操作设备ID集合
     */
    @NotEmpty(message = "设备ID集合不能为空")
    private List<Long> deviceIds;

    /**
     * 目标分组ID（挂载必填）
     */
    @NotNull(message = "分组ID不能为空")
    private Long groupId;

    /**
     * true=移出分组，false=挂入分组（默认挂入）
     */
    private Boolean remove;

}
