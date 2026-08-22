package com.nexus.mes.release.domain.bo;

import com.nexus.mes.release.domain.ReleasePkgFirmware;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * MES发布包固件映射业务对象 mes_release_pkg_firmware
 *
 * @author Nexus
 */
@Data
@AutoMapper(target = ReleasePkgFirmware.class, reverseConvertGenerate = false)
public class ReleasePkgFirmwareBo {

    /** 主键ID(保存时忽略, 先删后插) */
    private Long mappingId;

    /** 工艺路线步骤ID(精确定位, 与 processId 至少填一个) */
    private Long routingStepId;

    /** 工序ID(宽泛定位) */
    private Long processId;

    /** 工序编码(冗余) */
    private String processCode;

    /** 工序名称(冗余) */
    private String processName;

    /** PCBA物料ID(追溯用, 可空) */
    private Long pcbaMaterialId;

    /** PCBA物料编码(冗余) */
    private String pcbaMaterialCode;

    /** PCBA物料名称(冗余) */
    private String pcbaMaterialName;

    /** 固件ID(仅RELEASED且物料匹配发布包物料) */
    @NotNull(message = "固件不能为空")
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
}
