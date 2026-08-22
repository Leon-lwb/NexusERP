package com.nexus.mes.release.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.mes.release.domain.ReleasePkgFirmware;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * MES发布包固件映射视图对象 mes_release_pkg_firmware
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ReleasePkgFirmware.class)
public class ReleasePkgFirmwareVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long mappingId;

    /** 发布包ID */
    private Long packageId;

    /** 工艺路线步骤ID(精确定位) */
    private Long routingStepId;

    /** 工序ID(宽泛定位) */
    private Long processId;

    /** 工序编码 */
    @ExcelProperty("工序编码")
    private String processCode;

    /** 工序名称 */
    @ExcelProperty("工序名称")
    private String processName;

    /** PCBA物料ID(追溯用) */
    private Long pcbaMaterialId;

    /** PCBA物料编码 */
    private String pcbaMaterialCode;

    /** PCBA物料名称 */
    private String pcbaMaterialName;

    /** 固件ID */
    private Long firmwareId;

    /** 固件编码 */
    @ExcelProperty("固件编码")
    private String firmwareCode;

    /** 固件名称 */
    @ExcelProperty("固件名称")
    private String firmwareName;

    /** 固件版本 */
    @ExcelProperty("固件版本")
    private String firmwareVersion;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;
}
