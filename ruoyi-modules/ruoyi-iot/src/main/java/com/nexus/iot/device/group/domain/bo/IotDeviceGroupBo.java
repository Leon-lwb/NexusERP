package com.nexus.iot.device.group.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.iot.device.group.domain.IotDeviceGroup;

/**
 * IoT设备分组业务对象 iot_device_group
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = IotDeviceGroup.class, reverseConvertGenerate = false)
public class IotDeviceGroupBo extends BaseEntity {

    /**
     * 分组ID（新增为空）
     */
    private Long groupId;

    /**
     * 父分组ID(0=根分组)
     */
    private Long parentId;

    /**
     * 分组编码
     */
    @NotBlank(message = "分组编码不能为空")
    @Size(max = 64, message = "分组编码长度不能超过 64")
    private String groupCode;

    /**
     * 分组名称
     */
    @NotBlank(message = "分组名称不能为空")
    @Size(max = 128, message = "分组名称长度不能超过 128")
    private String groupName;

    /**
     * 显示顺序
     */
    private Integer orderNum;

    /**
     * 状态(0正常 1停用)
     */
    private String status;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    /**
     * 查询用：分组名称模糊匹配
     */
    private String groupNameLike;

}
