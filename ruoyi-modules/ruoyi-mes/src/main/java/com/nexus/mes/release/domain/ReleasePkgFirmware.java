package com.nexus.mes.release.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MES发布包固件映射对象 mes_release_pkg_firmware
 * <p>将发布包中的固件版本绑定到具体工序: routingStepId 精确定位 / processId 宽泛定位(二选一或都填),
 * 工单生成时按 发布包映射 -> 工艺路线步骤固件 -> 物料默认固件 优先级覆写。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_release_pkg_firmware")
public class ReleasePkgFirmware extends TenantEntity {

    /** 主键ID */
    @TableId(value = "mapping_id")
    private Long mappingId;

    /** 发布包ID(mes_release_package.package_id) */
    private Long packageId;

    /** 工艺路线步骤ID(bd_routing_step.step_id, 精确定位) */
    private Long routingStepId;

    /** 工序ID(bd_process.process_id, 宽泛定位) */
    private Long processId;

    /** 工序编码(冗余) */
    private String processCode;

    /** 工序名称(冗余) */
    private String processName;

    /** PCBA物料ID(固件物理载体, 追溯用, 可空) */
    private Long pcbaMaterialId;

    /** PCBA物料编码(冗余) */
    private String pcbaMaterialCode;

    /** PCBA物料名称(冗余) */
    private String pcbaMaterialName;

    /** 固件ID(mes_firmware.firmware_id, 仅RELEASED) */
    private Long firmwareId;

    /** 固件编码(冗余) */
    private String firmwareCode;

    /** 固件名称(冗余) */
    private String firmwareName;

    /** 固件版本(冗余) */
    private String firmwareVersion;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
