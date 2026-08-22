package com.nexus.mes.release.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MES发布包配置项对象 mes_release_pkg_config
 * <p>可扩展键值对配置。消费方式: 工单创建时全量快照到 pro_work_order.release_config;
 * FLASH 组按工序注入 pro_work_order_step.stepFlashConfig(键=flashTypeCode, 值=contentRefId)。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_release_pkg_config")
public class ReleasePkgConfig extends TenantEntity {

    /** 主键ID */
    @TableId(value = "config_id")
    private Long configId;

    /** 发布包ID(mes_release_package.package_id) */
    private Long packageId;

    /** 配置分组: FLASH/TEST_PARAM/LABEL/PACKAGING/PROCESS */
    private String configGroup;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 值类型: STRING/NUMBER/BOOLEAN/JSON */
    private String configType;

    /** 关联工序ID(空=全局生效) */
    private Long processId;

    /** 工序编码(冗余) */
    private String processCode;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
