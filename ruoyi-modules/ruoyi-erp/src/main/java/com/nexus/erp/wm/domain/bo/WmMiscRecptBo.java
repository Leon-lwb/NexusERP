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

/**
 * 杂项入库单业务对象 wm_misc_recpt
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmMiscRecpt.class)
public class WmMiscRecptBo extends BaseEntity {

    /**
     * 入库单ID
     */
    @NotNull(message = "杂项入库ID不能为空", groups = {EditGroup.class})
    private Long recptId;

    /**
     * 入库单编码
     */
    @NotBlank(message = "入库单号不能为空", groups = {AddGroup.class, EditGroup.class})
    private String recptCode;

    /**
     * 入库类型
     */
    private String recptType;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 仓库编码
     */
    private String warehouseCode;

    /**
     * 仓库名称
     */
    private String warehouseName;

    /**
     * 库区ID
     */
    private Long areaId;

    /**
     * 库区编码
     */
    private String areaCode;

    /**
     * 库区名称
     */
    private String areaName;

    /**
     * 入库日期
     */
    private Date recptDate;

    /**
     * 单据状态
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;

    /**
     * 杂项入库单行项目列表
     */
    private List<WmMiscRecptLine> wmMiscRecptLineList;
}
