package com.nexus.iot.device.group.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * IoT设备与标签关系对象 iot_device_tag_rel
 * <p>
 * 唯一键 {@code uk_device_tag(tenant_id, device_id, tag_id)} 保证同一设备同一标签不重复。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@TableName("iot_device_tag_rel")
public class IotDeviceTagRel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关系ID
     */
    @TableId(value = "rel_id")
    private Long relId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 标签ID
     */
    private Long tagId;

    /**
     * 创建时间
     */
    private Date createTime;

}
