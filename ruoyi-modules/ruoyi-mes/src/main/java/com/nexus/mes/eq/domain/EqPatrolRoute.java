package com.nexus.mes.eq.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MES巡检路线对象 eq_patrol_route
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eq_patrol_route")
public class EqPatrolRoute extends TenantEntity {

    @TableId(value = "route_id")
    private Long routeId;

    /** 路线名称 */
    private String routeName;

    /** 路线描述 */
    private String description;

    /** 设备ID列表(JSON数组) */
    private String equipmentIds;

    /** 通用检查项(JSON数组) */
    private String checkItems;

    /** 频率: DAILY/WEEKLY/MONTHLY */
    private String frequency;

    /** 状态: ACTIVE/INACTIVE */
    private String status;

    /** 备注 */
    private String remark;

    @TableLogic
    private String delFlag;
}
