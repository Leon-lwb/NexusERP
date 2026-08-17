package com.nexus.mes.flash.domain.bo;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MesContentFileBo extends BaseEntity {

    private Long contentFileId;
    private String contentCode;

    @NotBlank(message = "配置名称不能为空", groups = {AddGroup.class})
    private String contentName;

    private String contentType;

    @NotNull(message = "适用物料不能为空", groups = {AddGroup.class})
    private Long materialId;

    private String materialCode;
    private String materialName;

    @NotBlank(message = "版本号不能为空", groups = {AddGroup.class})
    private String version;

    private Long ossId;
    private String fileMd5;
    private Long fileSize;
    private String releaseNote;
    private String status;
    private String isDefault;
    private String remark;
}
