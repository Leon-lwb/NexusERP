package com.nexus.iot.device.group.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.iot.device.group.domain.IotDeviceTag;

/**
 * IoT设备标签业务对象 iot_device_tag
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = IotDeviceTag.class, reverseConvertGenerate = false)
public class IotDeviceTagBo extends BaseEntity {

    /**
     * 标签ID（新增为空）
     */
    private Long tagId;

    /**
     * 标签名称
     */
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 64, message = "标签名称长度不能超过 64")
    private String tagName;

    /**
     * 标签颜色
     */
    @Size(max = 32, message = "标签颜色长度不能超过 32")
    private String tagColor;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
