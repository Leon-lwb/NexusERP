package com.nexus.mes.flash.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 资源池明细对象 mes_resource_item
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_resource_item")
public class MesResourceItem extends TenantEntity {

    @TableId(value = "item_id")
    private Long itemId;

    private Long poolId;
    /** 资源值(如MAC 00:1A:2B:3C:4D:5E) */
    private String resourceValue;
    /** AVAILABLE/ALLOCATED/USED/SCRAPPED */
    private String status;
    /** 已绑定SN ID(一SN绑一资源) */
    private Long boundSnId;
    private Date boundTime;
    private String remark;
    @TableLogic
    private String delFlag;
}
