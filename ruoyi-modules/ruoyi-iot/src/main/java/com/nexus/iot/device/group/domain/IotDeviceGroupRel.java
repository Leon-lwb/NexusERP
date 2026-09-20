package com.nexus.iot.device.group.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * IoT设备与分组关系对象 iot_device_group_rel
 * <p>
 * 纯关系表（无审计列/逻辑删除），挂载与移出为物理增删：
 * 唯一键 {@code uk_device_group(tenant_id, device_id, group_id)} 保证同一设备同一分组不重复，
 * 重复挂载依赖唯一键幂等（插入前先查已有关系，避免异常路径）。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@TableName("iot_device_group_rel")
public class IotDeviceGroupRel implements Serializable {

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
     * 分组ID
     */
    private Long groupId;

    /**
     * 创建时间
     */
    private Date createTime;

}
