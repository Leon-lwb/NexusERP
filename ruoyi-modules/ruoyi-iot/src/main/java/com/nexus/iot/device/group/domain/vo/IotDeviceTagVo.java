package com.nexus.iot.device.group.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.iot.device.group.domain.IotDeviceTag;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * IoT设备标签视图对象 iot_device_tag
 *
 * @author nexus
 */
@Data
@AutoMapper(target = IotDeviceTag.class)
public class IotDeviceTagVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 标签ID
     */
    private Long tagId;

    /**
     * 标签名称
     */
    private String tagName;

    /**
     * 标签颜色
     */
    private String tagColor;

    /**
     * 备注
     */
    private String remark;

    /**
     * 已绑定设备数（列表展示用，由服务端填充）
     */
    private Long deviceCount;

    /**
     * 创建时间
     */
    private Date createTime;

}
