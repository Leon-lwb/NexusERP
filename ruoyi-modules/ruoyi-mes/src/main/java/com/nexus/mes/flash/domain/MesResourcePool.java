package com.nexus.mes.flash.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 刷写资源池对象 mes_resource_pool
 * <p>MAC/SN 等资源池, INTERNAL(内部导入) 或 EXTERNAL(外部REST); 刷写时原子分配一资源给SN。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_resource_pool")
public class MesResourcePool extends TenantEntity {

    @TableId(value = "pool_id")
    private Long poolId;

    /** 资源池编码(MES_POOL_CODE) */
    private String poolCode;
    private String poolName;
    /** 资源类型: MAC/SN/CERT */
    private String resourceType;
    /** 来源: INTERNAL/EXTERNAL */
    private String source;
    /** 外部REST配置JSON */
    private String externalConfig;
    /** 分配策略: FIFO/随机 */
    private String allocStrategy;
    private Integer totalCount;
    private Integer usedCount;
    /** ENABLED/DISABLED */
    private String status;
    private String remark;
    @TableLogic
    private String delFlag;
}
