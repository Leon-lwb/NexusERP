package com.nexus.iot.device.group.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

/**
 * IoT设备标签对象 iot_device_tag
 * <p>
 * 标签是"轻量、可多值、可跨分组复用"的运维维度（如「关键设备」「待检修」「A 类」），
 * 与分组（层级归属，唯一主维度）互补，故用独立表 + {@link IotDeviceTagRel} 关系表表达，
 * 而非在设备表上放 JSON 列：关系表可建索引、可按标签集合做集合筛选与统计。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("iot_device_tag")
public class IotDeviceTag extends TenantEntity {

    /**
     * 标签ID
     */
    @TableId(value = "tag_id")
    private Long tagId;

    /**
     * 标签名称
     */
    private String tagName;

    /**
     * 标签颜色(前端展示)
     */
    private String tagColor;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志(0正常 1删除)
     */
    @TableLogic
    private String delFlag;

}
