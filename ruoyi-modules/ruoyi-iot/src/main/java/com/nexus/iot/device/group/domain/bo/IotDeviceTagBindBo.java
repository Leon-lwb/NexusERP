package com.nexus.iot.device.group.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 设备批量打标签 / 解绑标签请求体。
 *
 * @author nexus
 */
@Data
public class IotDeviceTagBindBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 待操作设备ID集合
     */
    @NotEmpty(message = "设备ID集合不能为空")
    private List<Long> deviceIds;

    /**
     * 标签ID集合（绑定必填）
     */
    @NotEmpty(message = "标签ID集合不能为空")
    private List<Long> tagIds;

    /**
     * true=解绑，false=绑定（默认绑定）
     */
    private Boolean remove;

}
