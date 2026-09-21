package com.nexus.iot.device.group.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.iot.device.group.domain.IotDeviceGroup;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * IoT设备分组视图对象 iot_device_group
 *
 * @author nexus
 */
@Data
@AutoMapper(target = IotDeviceGroup.class)
public class IotDeviceGroupVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分组ID
     */
    private Long groupId;

    /**
     * 父分组ID(0=根分组)
     */
    private Long parentId;

    /**
     * 祖级列表
     */
    private String ancestors;

    /**
     * 分组编码
     */
    private String groupCode;

    /**
     * 分组名称
     */
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
    private String remark;

    /**
     * 该分组下已挂载设备数（列表展示用，由服务端填充）
     */
    private Long deviceCount;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 子分组（树形返回时填充；平铺返回时为空）
     */
    private List<IotDeviceGroupVo> children = new ArrayList<>();

}
