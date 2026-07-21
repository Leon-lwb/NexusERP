package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.util.Date;
import java.util.List;

/**
 * 杂项入库单对象 wm_misc_recpt
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_misc_recpt")
public class WmMiscRecpt extends TenantEntity {

    /**
     * 入库单ID
     */
    @TableId(value = "recpt_id")
    private Long recptId;

    /**
     * 入库单编码
     */
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
    @TableField(exist = false)
    private List<WmMiscRecptLine> wmMiscRecptLineList;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}
