package com.nexus.station.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 关键件绑定操作日志对象 mes_sn_bind_log
 * <p>C5 绑定/C6 解绑换板留痕(操作审计与追溯)。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_sn_bind_log")
public class MesSnBindLog extends TenantEntity {

    /** 日志ID(主键) */
    @TableId(value = "log_id")
    private Long logId;

    /** 操作: BIND/UNBIND/REBIND */
    private String action;

    /** 父SN ID */
    private Long parentSnId;

    /** 父SN编码 */
    private String parentSnCode;

    /** 组件SN ID */
    private Long componentSnId;

    /** 组件SN编码 */
    private String componentSnCode;

    /** 换板新组件SN编码(REBIND) */
    private String newComponentSnCode;

    /** 操作原因(解绑/换板必填) */
    private String reason;

    /** 操作员工号 */
    private String operatorCode;
}
