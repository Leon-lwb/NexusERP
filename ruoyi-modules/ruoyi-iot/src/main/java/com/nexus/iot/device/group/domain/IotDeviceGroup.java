package com.nexus.iot.device.group.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

/**
 * IoT设备分组对象 iot_device_group
 * <p>
 * 树形分组（与 {@code sys_dept} 同构）：{@code parent_id} + {@code ancestors} 物化路径，
 * 按 {@code ancestors} 前缀即可一次查出整棵子树，避免递归 SQL。
 * <p>
 * 设备与分组的关系放在 {@link IotDeviceGroupRel}（N:N），而非在 {@code iot_device} 上加 {@code group_id} 列：
 * <ol>
 *   <li>同一台设备常需同时归属「车间 / 产线 / 工艺段」等多个视角，单列无法表达；</li>
 *   <li>不改动 {@code iot_device} 表结构，可避免主脚本中 {@code iot_device} 的
 *       <b>无列名位序 INSERT</b> 存量行因列数变化而全部失效。</li>
 * </ol>
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("iot_device_group")
public class IotDeviceGroup extends TenantEntity {

    /**
     * 分组ID
     */
    @TableId(value = "group_id")
    private Long groupId;

    /**
     * 父分组ID(0=根分组)
     */
    private Long parentId;

    /**
     * 祖级列表(逗号分隔,不含自身;根为0)
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
     * 删除标志(0正常 1删除)
     */
    @TableLogic
    private String delFlag;

}
