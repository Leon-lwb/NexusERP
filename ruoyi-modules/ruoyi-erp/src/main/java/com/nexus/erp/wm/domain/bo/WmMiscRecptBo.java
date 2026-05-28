package com.nexus.erp.wm.domain.bo;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmMiscRecpt;
import com.nexus.erp.wm.domain.WmMiscRecptLine;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmMiscRecpt.class)
public class WmMiscRecptBo extends BaseEntity {

    @NotNull(message = "杂项入库ID不能为空", groups = {EditGroup.class})
    private Long recptId;

    @NotBlank(message = "入库单号不能为空", groups = {AddGroup.class, EditGroup.class})
    private String recptCode;

    private String recptType;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private Long areaId;
    private String areaCode;
    private String areaName;
    private Date recptDate;
    private String status;
    private String remark;
    private String attr1;
    private String attr2;
    private Long attr3;
    private Long attr4;

    private List<WmMiscRecptLine> wmMiscRecptLineList;
}
