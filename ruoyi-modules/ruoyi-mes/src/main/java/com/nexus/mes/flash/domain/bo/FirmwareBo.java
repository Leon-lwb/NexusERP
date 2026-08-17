package com.nexus.mes.flash.domain.bo;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.flash.domain.Firmware;

/**
 * MES固件档案业务对象 mes_firmware
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = Firmware.class)
public class FirmwareBo extends BaseEntity {

    /** 固件ID */
    @Null(groups = {AddGroup.class})
    @NotNull(groups = {EditGroup.class})
    private Long firmwareId;

    /** 固件编码(查询用;新增留空自动生成 MES_FW_CODE) */
    private String firmwareCode;

    /** 固件名称 */
    @NotBlank(message = "固件名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String firmwareName;

    /** 适用物料ID(产品) */
    @NotNull(message = "适用物料不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long materialId;

    /** 适用物料编码 */
    private String materialCode;

    /** 适用物料名称 */
    private String materialName;

    /** 版本号(如 1.2.3) */
    @NotBlank(message = "版本号不能为空", groups = {AddGroup.class, EditGroup.class})
    private String version;

    /** 构建号 */
    private String buildNo;

    /** 固件文件OSS文件ID(sys_oss) */
    @NotNull(message = "固件文件不能为空,请先上传", groups = {AddGroup.class})
    private Long ossId;

    /** 固件文件MD5 */
    private String fileMd5;

    /** 固件文件大小(字节) */
    private Long fileSize;

    /** 发布说明 */
    private String releaseNote;

    /** 状态: DRAFT/IN_REVIEW/RELEASED/ARCHIVED(查询用) */
    private String status;

    /** 备注 */
    private String remark;
}